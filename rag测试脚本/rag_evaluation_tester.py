#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
RAG 系统自动化测试与评估脚本
=================================

工作流程：
  1. 从 JSON / CSV 文件加载测试用例（每条约含 kbId、question、keyword、expected_answer）
  2. 并发调用 Java RAG API → 获取 answer、contexts
  3. Python 端调用 RAGAS（或降级方案）计算各项评估指标
  4. 导出 evaluation_report.csv 和 evaluation_summary.json

使用示例：
  # 最简用法（无需评估，仅收集 API 返回）
  python rag_evaluation_tester.py -d test_dataset.json

  # 启用 RAGAS 评估（需要 pip install ragas）
  python rag_evaluation_tester.py -d test_dataset.json --ragas

  # 指定并发、超时、RAGAS 模型后端
  python rag_evaluation_tester.py -d test_dataset.json --ragas \
      --max-workers 5 --timeout 90
"""

from __future__ import annotations

import argparse
import csv
import json
import logging
import os
import re
import sys
import textwrap
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from dataclasses import dataclass, field, asdict
from typing import Any, Dict, List, Optional, Tuple

import requests

# ---------------------------------------------------------------------------
# 日志
# ---------------------------------------------------------------------------
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
    datefmt="%Y-%m-%d %H:%M:%S",
)
logger = logging.getLogger("rag_eval")

# ---------------------------------------------------------------------------
# 数据集结构
# ---------------------------------------------------------------------------
@dataclass
class TestCase:
    """单条测试用例 — 对应 Java Controller 的入参"""
    id: str                              # 用例唯一标识
    kb_id: str                           # 知识库 ID（必填）
    question: str                        # 测试问题
    keyword: Optional[str] = None        # 混合检索关键词（若为空则用 question）
    expected_answer: Optional[str] = None  # 期望答案（RAGAS 的 ground_truth）
    extra: Dict[str, Any] = field(default_factory=dict)


@dataclass
class RawResponse:
    """Java API 原始返回"""
    question: str
    answer: Optional[str] = None
    contexts: List[str] = field(default_factory=list)
    latency_ms: float = 0.0


@dataclass
class ScoreSet:
    """一次评估产生的分数集合"""
    # RAGAS 标准指标
    answer_relevancy: Optional[float] = None       # 答案相关性
    faithfulness: Optional[float] = None           # 忠实度（答案是否来自 context）
    context_precision: Optional[float] = None      # 上下文精确度
    context_recall: Optional[float] = None         # 上下文召回率
    # 基础降级指标（RAGAS 不可用时使用）
    answer_length: int = 0
    avg_context_length: float = 0.0
    keyword_overlap: Optional[float] = None        # question 关键词在 answer 中的覆盖率
    context_answer_overlap: Optional[float] = None # context 与 answer 的词汇重叠度


@dataclass
class EvalResult:
    """单条用例的完整评估结果"""
    case_id: str
    kb_id: str
    question: str
    keyword: Optional[str]
    status: str                     # "success" / "timeout" / "http_error" / "json_error" / "exception"
    http_status: Optional[int] = None
    answer: Optional[str] = None
    contexts: List[str] = field(default_factory=list)
    context_count: int = 0
    latency_ms: Optional[float] = None
    # 评估分数（成功时填充）
    scores: Optional[ScoreSet] = None
    # 失败信息
    error_message: Optional[str] = None

# ---------------------------------------------------------------------------
# 数据加载
# ---------------------------------------------------------------------------
def load_test_data(file_path: str) -> List[TestCase]:
    """从 JSON 或 CSV 文件加载测试用例。"""
    if not os.path.exists(file_path):
        raise FileNotFoundError(f"测试数据文件不存在: {file_path}")

    ext = os.path.splitext(file_path)[1].lower()
    loader = _load_from_json if ext == ".json" else _load_from_csv if ext == ".csv" else None
    if loader is None:
        raise ValueError(f"不支持的文件格式: {ext}，请使用 .json 或 .csv")

    cases = loader(file_path)
    logger.info("从 %s 加载了 %d 条测试用例", file_path, len(cases))
    return cases


def _load_from_json(file_path: str) -> List[TestCase]:
    with open(file_path, "r", encoding="utf-8") as f:
        data = json.load(f)
    if not isinstance(data, list):
        raise ValueError("JSON 文件的顶层结构必须是一个数组")
    return [_parse_case(item, i) for i, item in enumerate(data)]


def _load_from_csv(file_path: str) -> List[TestCase]:
    cases = []
    with open(file_path, "r", encoding="utf-8-sig") as f:
        for i, row in enumerate(csv.DictReader(f)):
            cases.append(_parse_case(row, i))
    return cases


def _parse_case(item: dict, index: int) -> TestCase:
    """字典 → TestCase，自动兼容大小写变体。"""
    get = lambda *keys: next((item[k] for k in keys if k in item and item[k] is not None), None)

    question = get("question", "Question")
    kb_id = get("kbId", "kb_id", "KBId", "kbID")
    if not question or not kb_id:
        raise ValueError(
            f"第 {index + 1} 条用例缺少必填字段 "
            f"(question={'OK' if question else 'MISSING'}, kbId={'OK' if kb_id else 'MISSING'})"
        )

    known = {"id", "ID", "question", "Question",
             "kbId", "kb_id", "KBId", "kbID",
             "keyword", "Keyword",
             "expected_answer", "ExpectedAnswer", "ground_truth"}

    return TestCase(
        id=str(get("id", "ID") or f"case_{index + 1:03d}"),
        kb_id=str(kb_id),
        question=str(question),
        keyword=get("keyword", "Keyword"),
        expected_answer=get("expected_answer", "ExpectedAnswer", "ground_truth"),
        extra={k: v for k, v in item.items() if k not in known},
    )

# ---------------------------------------------------------------------------
# HTTP 请求
# ---------------------------------------------------------------------------
def build_payload(tc: TestCase, global_kb_id: Optional[str]) -> dict:
    """
    构造 Java 接口的请求体。
    POST /api/rag/evaluate/run  接受: { "kbId", "question", "keyword" }
    """
    return {
        "kbId": tc.kb_id or global_kb_id or "",
        "question": tc.question,
        "keyword": tc.keyword or tc.question,
    }


def _post_one(
    session: requests.Session,
    tc: TestCase,
    api_url: str,
    timeout: int,
    global_kb_id: Optional[str],
) -> Tuple[EvalResult, Optional[RawResponse]]:
    """发送单条请求 → (EvalResult, RawResponse or None)"""
    time.sleep(2)
    payload = build_payload(tc, global_kb_id)
    t0 = time.perf_counter()

    try:
        resp = session.post(api_url, json=payload, timeout=timeout,
                            headers={"Content-Type": "application/json"})
        latency = round((time.perf_counter() - t0) * 1000, 2)

        if resp.status_code != 200:
            logger.warning("[%s] HTTP %d", tc.id, resp.status_code)
            return (EvalResult(
                case_id=tc.id, kb_id=tc.kb_id or (global_kb_id or ""),
                question=tc.question, keyword=tc.keyword,
                status="http_error", http_status=resp.status_code,
                latency_ms=latency,
                error_message=f"HTTP {resp.status_code}: {resp.text[:500]}",
            ), None)

        try:
            body = resp.json()
        except json.JSONDecodeError as e:
            logger.warning("[%s] JSON 解析失败: %s", tc.id, e)
            return (EvalResult(
                case_id=tc.id, kb_id=tc.kb_id or (global_kb_id or ""),
                question=tc.question, keyword=tc.keyword,
                status="json_error", http_status=resp.status_code,
                latency_ms=latency, error_message=str(e),
            ), None)

        raw = RawResponse(
            question=body.get("question", tc.question),
            answer=body.get("answer"),
            contexts=body.get("contexts") or body.get("retrieved_contexts") or [],
            latency_ms=latency,
        )

        result = EvalResult(
            case_id=tc.id, kb_id=tc.kb_id or (global_kb_id or ""),
            question=tc.question, keyword=tc.keyword,
            status="success", http_status=resp.status_code,
            answer=raw.answer,
            contexts=raw.contexts,
            context_count=len(raw.contexts),
            latency_ms=latency,
        )
        return (result, raw)

    except requests.exceptions.Timeout:
        return (EvalResult(
            case_id=tc.id, kb_id=tc.kb_id or (global_kb_id or ""),
            question=tc.question, keyword=tc.keyword,
            status="timeout",
            latency_ms=round((time.perf_counter() - t0) * 1000, 2),
            error_message=f"请求超时 (>{timeout}s)",
        ), None)
    except requests.exceptions.ConnectionError as e:
        return (EvalResult(
            case_id=tc.id, kb_id=tc.kb_id or (global_kb_id or ""),
            question=tc.question, keyword=tc.keyword,
            status="exception",
            latency_ms=round((time.perf_counter() - t0) * 1000, 2),
            error_message=f"连接失败: {str(e)[:500]}",
        ), None)
    except Exception as e:
        logger.error("[%s] 未知异常: %s", tc.id, e, exc_info=True)
        return (EvalResult(
            case_id=tc.id, kb_id=tc.kb_id or (global_kb_id or ""),
            question=tc.question, keyword=tc.keyword,
            status="exception",
            latency_ms=round((time.perf_counter() - t0) * 1000, 2),
            error_message=f"{type(e).__name__}: {str(e)[:500]}",
        ), None)

# ---------------------------------------------------------------------------
# 批量执行
# ---------------------------------------------------------------------------
def run_requests(
    test_cases: List[TestCase],
    api_url: str,
    max_workers: int,
    timeout: int,
    global_kb_id: Optional[str],
) -> Tuple[List[EvalResult], List[Tuple[int, RawResponse]]]:
    """并发发送请求，返回 (results, raw_pairs)。raw_pairs = [(case_index, RawResponse), ...]"""
    total = len(test_cases)
    results: List[EvalResult] = []
    raw_pairs: List[Tuple[int, RawResponse]] = []
    completed = 0
    failed = 0

    logger.info("=" * 60)
    logger.info("请求阶段: %d 条用例 (并发=%d, 超时=%ds)", total, max_workers, timeout)
    logger.info("目标 API: %s", api_url)
    logger.info("=" * 60)

    t_start = time.perf_counter()

    with ThreadPoolExecutor(max_workers=max_workers) as pool:
        fut_map = {}
        for idx, tc in enumerate(test_cases):
            sess = requests.Session()
            fut = pool.submit(_post_one, sess, tc, api_url, timeout, global_kb_id)
            fut_map[fut] = idx

        for fut in as_completed(fut_map):
            idx = fut_map[fut]
            try:
                result, raw = fut.result()
            except Exception as e:
                logger.error("[%s] 线程异常: %s", test_cases[idx].id, e)
                result = EvalResult(
                    case_id=test_cases[idx].id,
                    kb_id=test_cases[idx].kb_id or (global_kb_id or ""),
                    question=test_cases[idx].question,
                    keyword=test_cases[idx].keyword,
                    status="exception",
                    error_message=f"线程异常: {str(e)[:500]}",
                )
                raw = None

            results.append(result)
            if raw is not None:
                raw_pairs.append((idx, raw))

            completed += 1
            if result.status != "success":
                failed += 1

            if total <= 10 or completed % max(1, total // 10) == 0 or completed == total:
                pct = completed * 100 // total
                bar = "#" * (pct // 5) + "-" * (20 - pct // 5)
                logger.info("[%s] %3d%% (%d/%d) 成功=%d 失败=%d",
                            bar, pct, completed, total, completed - failed, failed)

    elapsed = time.perf_counter() - t_start
    logger.info("请求阶段完成 → %.2fs | 成功=%d | 失败=%d",
                elapsed, total - failed, failed)
    return results, raw_pairs

# ---------------------------------------------------------------------------
# 评估引擎
# ---------------------------------------------------------------------------
class BasicEvaluator:
    """
    不依赖外部 LLM 的基础评估器。
    当 RAGAS 不可用或未启用时使用。
    """

    @staticmethod
    def evaluate(question: str, answer: str, contexts: List[str],
                 expected_answer: Optional[str] = None) -> ScoreSet:
        scores = ScoreSet()
        if not answer:
            return scores

        answer_lower = answer.lower()
        question_lower = question.lower()
        contexts_text = " ".join(c.lower() for c in contexts)

        # 答案长度
        scores.answer_length = len(answer)
        scores.avg_context_length = sum(len(c) for c in contexts) / max(len(contexts), 1)

        # question → answer 关键词覆盖率
        q_words = set(_tokenize(question_lower))
        a_words = set(_tokenize(answer_lower))
        if q_words:
            scores.keyword_overlap = len(q_words & a_words) / len(q_words)

        # context → answer 词汇重叠度（近似 faithfulness）
        ctx_words = set(_tokenize(contexts_text))
        if ctx_words and a_words:
            scores.context_answer_overlap = len(ctx_words & a_words) / len(a_words)

        # 如果有期望答案，做 BLEU-like 简单重叠（近似 answer_relevancy）
        if expected_answer:
            exp_words = set(_tokenize(expected_answer.lower()))
            if exp_words:
                scores.answer_relevancy = len(exp_words & a_words) / len(exp_words)

        return scores


def _tokenize(text: str) -> List[str]:
    """简易中文/英文分词。"""
    # 提取中文字符和英文单词
    tokens = re.findall(r"[一-鿿]|[a-zA-Z0-9]+", text)
    return [t for t in tokens if len(t) > 1 or t.isalpha()]  # 过滤单数字


# --- RAGAS 评估器 -----------------------------------------------------------
_RAGAS_AVAILABLE = False
try:
    import pandas as pd
    from ragas import evaluate as ragas_evaluate, EvaluationDataset
    from ragas.llms import llm_factory
    from ragas.embeddings import OpenAIEmbeddings as RagasOpenAIEmbeddings
    from ragas.metrics import context_precision, context_recall
    from openai import OpenAI as OpenAIClient
    _RAGAS_AVAILABLE = True
except ImportError:
    pass


class RagasEvaluator:
    """
    基于 RAGAS 库的 LLM 评估器（兼容 ragas >= 0.4.0）。
    评估指标：上下文精确度 (Context Precision)、上下文召回率 (Context Recall)

    需要:
      pip install ragas langchain-openai pandas langchain-google-vertexai
    并使用兼容 OpenAI 协议的 LLM 后端。
    """

    def __init__(self, openai_api_base: str, openai_api_key: str,
                 model: str, embedding_model: str = "text-embedding-3-small"):
        if not _RAGAS_AVAILABLE:
            raise RuntimeError(
                "RAGAS 不可用。请安装: pip install ragas langchain-openai pandas"
            )

        # 创建 OpenAI client 用于 ragas 的 llm_factory / embeddings
        _client = OpenAIClient(api_key=openai_api_key, base_url=openai_api_base)

        # llm_factory 创建 InstructorBaseRagasLLM（兼容旧版 evaluate() 的 llm 参数）
        self._llm = llm_factory(model, client=_client)
        self.embeddings = RagasOpenAIEmbeddings(
            client=_client, model=embedding_model,
        )

        # 旧版 ragas.metrics 指标（Metric 实例，兼容 evaluate()）
        self.metrics = [context_precision, context_recall]

    def evaluate_batch(self, samples: List[dict]) -> List[ScoreSet]:
        """
        samples: [{user_input, response, retrieved_contexts, reference}, ...]
        """
        # Normalize retrieved_contexts to list[str]
        for s in samples:
            ctx = s.get("retrieved_contexts", [])
            if not isinstance(ctx, list):
                s["retrieved_contexts"] = [ctx] if ctx else []

        ds = EvaluationDataset.from_list(samples)

        result = ragas_evaluate(
            dataset=ds,
            metrics=self.metrics,
            llm=self._llm,
            embeddings=self.embeddings,
        )

        # EvaluationResult → pandas → 提取分数
        result_df = result.to_pandas()

        scores_list: List[ScoreSet] = []
        for _, row in result_df.iterrows():
            scores_list.append(ScoreSet(
                context_precision=_safe_float(row, "context_precision"),
                context_recall=_safe_float(row, "context_recall"),
            ))
        return scores_list


def _safe_float(row, col: str) -> Optional[float]:
    """从 DataFrame 行安全提取浮点数。"""
    try:
        v = row[col]
        return float(v) if pd.notna(v) else None
    except (KeyError, ValueError, TypeError):
        return None


def run_evaluation_phase(
    results: List[EvalResult],
    raw_pairs: List[Tuple[int, RawResponse]],
    test_cases: List[TestCase],
    ragas_evaluator: Optional[RagasEvaluator],
) -> List[EvalResult]:
    """
    对成功的结果填充评估分数。
    - 如果 ragas_evaluator 可用：批量 RAGAS 评估
    - 否则：使用 BasicEvaluator
    """
    if not ragas_evaluator:
        logger.info("评估阶段: 使用 BasicEvaluator（无 LLM）")
        for r in results:
            if r.status == "success" and r.answer:
                # 找到对应的 TestCase 以获得 expected_answer
                tc = next((t for t in test_cases if t.id == r.case_id), None)
                expected = tc.expected_answer if tc else None
                r.scores = BasicEvaluator.evaluate(
                    r.question, r.answer, r.contexts, expected
                )
        return results

    # RAGAS 路径
    logger.info("评估阶段: 使用 RAGAS (LLM) 评估 %d 条成功用例", len(raw_pairs))

    # 构建 RAGAS 输入
    samples: List[dict] = []
    idx_to_result_map: Dict[int, EvalResult] = {}

    for sample_idx, (case_idx, raw) in enumerate(raw_pairs):
        tc = test_cases[case_idx]
        samples.append({
            "user_input": raw.question,
            "response": raw.answer or "",
            "retrieved_contexts": raw.contexts,
            "reference": tc.expected_answer or "",
        })
        # 在 results 中找到对应的 result
        for r in results:
            if r.case_id == tc.id and r.status == "success":
                idx_to_result_map[sample_idx] = r
                break

    if not samples:
        logger.warning("没有可评估的样本")
        return results

    try:
        scores = ragas_evaluator.evaluate_batch(samples)
        for i, score in enumerate(scores):
            if i in idx_to_result_map:
                idx_to_result_map[i].scores = score
        logger.info("RAGAS 评估完成 (%d 条)", len(scores))
    except Exception as e:
        msg = str(e)
        # 精简常见错误的日志
        if "401" in msg or "Authorization" in msg or "invalid" in msg:
            logger.error(
                "RAGAS 评估失败: API Key 认证不通过 (401)。"
                "请检查 --ragas-api-key 是否正确，回退到 BasicEvaluator"
            )
        elif "402" in msg:
            logger.error(
                "RAGAS 评估失败: 账户余额不足 (402)，回退到 BasicEvaluator"
            )
        else:
            logger.error("RAGAS 评估异常: %s，回退到 BasicEvaluator", msg[:200])
        for r in results:
            if r.status == "success" and r.answer:
                tc = next((t for t in test_cases if t.id == r.case_id), None)
                expected = tc.expected_answer if tc else None
                r.scores = BasicEvaluator.evaluate(
                    r.question, r.answer, r.contexts, expected
                )

    return results

# ---------------------------------------------------------------------------
# 汇总统计
# ---------------------------------------------------------------------------
def compute_summary(results: List[EvalResult], with_ragas: bool) -> dict:
    total = len(results)
    if total == 0:
        return {"total": 0}

    success = [r for r in results if r.status == "success"]
    failed  = [r for r in results if r.status != "success"]
    scored  = [r for r in success if r.scores is not None]

    def _stats(getter, precision=2):
        vals = [getter(r.scores) for r in scored if getter(r.scores) is not None]
        if not vals:
            return {}
        return {
            "count": len(vals),
            "min": round(min(vals), precision),
            "max": round(max(vals), precision),
            "avg": round(sum(vals) / len(vals), precision),
        }

    latencies = [r.latency_ms for r in success if r.latency_ms is not None]

    # 失败分类
    failure_breakdown: Dict[str, int] = {}
    for r in failed:
        failure_breakdown[r.status] = failure_breakdown.get(r.status, 0) + 1

    # 按 kbId 分组
    kb_stats: Dict[str, dict] = {}
    for r in results:
        kb = r.kb_id or "(default)"
        if kb not in kb_stats:
            kb_stats[kb] = {"total": 0, "success": 0, "failed": 0, "avg_latency_ms": 0.0}
        kb_stats[kb]["total"] += 1
        kb_stats[kb]["success" if r.status == "success" else "failed"] += 1

    for kb, ks in kb_stats.items():
        kb_lats = [r.latency_ms for r in success
                   if (r.kb_id or "(default)") == kb and r.latency_ms is not None]
        if kb_lats:
            ks["avg_latency_ms"] = round(sum(kb_lats) / len(kb_lats), 2)

    return {
        "total": total,
        "success_count": len(success),
        "failed_count": len(failed),
        "success_rate": round(len(success) / total * 100, 2) if total else 0.0,
        "latency_ms": _stats_from_list(latencies),
        "answer_relevancy": _stats(lambda s: s.answer_relevancy),
        "faithfulness": _stats(lambda s: s.faithfulness),
        "context_precision": _stats(lambda s: s.context_precision),
        "context_recall": _stats(lambda s: s.context_recall),
        "keyword_overlap": _stats(lambda s: s.keyword_overlap),
        "context_answer_overlap": _stats(lambda s: s.context_answer_overlap),
        "failure_breakdown": failure_breakdown,
        "by_kb_id": kb_stats if len(kb_stats) > 1 else None,
        "evaluation_method": "ragas" if with_ragas else "basic",
    }


def _stats_from_list(vals: List[float]) -> dict:
    if not vals:
        return {}
    return {
        "count": len(vals),
        "min": round(min(vals), 2),
        "max": round(max(vals), 2),
        "avg": round(sum(vals) / len(vals), 2),
    }

# ---------------------------------------------------------------------------
# 导出
# ---------------------------------------------------------------------------
MAX_CELL = 500

def export_csv(results: List[EvalResult], file_path: str):
    if not results:
        logger.warning("没有结果可导出")
        return

    fieldnames = [
        "case_id", "kb_id", "question", "keyword",
        "status", "http_status", "answer", "context_count",
        "latency_ms",
        "answer_relevancy", "faithfulness", "context_precision", "context_recall",
        "keyword_overlap", "context_answer_overlap",
        "error_message",
    ]

    with open(file_path, "w", encoding="utf-8-sig", newline="") as f:
        w = csv.DictWriter(f, fieldnames=fieldnames, extrasaction="ignore")
        w.writeheader()
        for r in results:
            s = r.scores or ScoreSet()
            w.writerow({
                "case_id": r.case_id,
                "kb_id": r.kb_id,
                "question": _t(r.question, MAX_CELL),
                "keyword": r.keyword or "",
                "status": r.status,
                "http_status": r.http_status or "",
                "answer": _t(r.answer, MAX_CELL),
                "context_count": r.context_count,
                "latency_ms": r.latency_ms if r.latency_ms is not None else "",
                "answer_relevancy": _f(s.answer_relevancy),
                "faithfulness": _f(s.faithfulness),
                "context_precision": _f(s.context_precision),
                "context_recall": _f(s.context_recall),
                "keyword_overlap": _f(s.keyword_overlap),
                "context_answer_overlap": _f(s.context_answer_overlap),
                "error_message": _t(r.error_message, MAX_CELL),
            })
    logger.info("CSV 报告 → %s (%d 条)", file_path, len(results))


def export_json_summary(results: List[EvalResult], summary: dict, file_path: str):
    def _to_dict(r: EvalResult) -> dict:
        d = asdict(r, dict_factory=lambda x: {k: v for k, v in x if k != "contexts"})
        # 只保留前 5 条 context 摘要
        d["contexts"] = [ctx[:200] for ctx in r.contexts[:5]]
        d["context_total"] = r.context_count
        if d.get("answer") and len(d["answer"]) > 2000:
            d["answer"] = d["answer"][:2000] + "..."
        if d.get("error_message") and len(d["error_message"]) > 1000:
            d["error_message"] = d["error_message"][:1000] + "..."
        if r.scores:
            d["scores"] = {k: v for k, v in asdict(r.scores).items() if v is not None}
        return d

    output = {"summary": summary, "details": [_to_dict(r) for r in results]}
    with open(file_path, "w", encoding="utf-8") as f:
        json.dump(output, f, ensure_ascii=False, indent=2)
    logger.info("JSON 汇总 → %s", file_path)


def _t(text: Optional[str], limit: int) -> str:
    if not text: return ""
    return text if len(text) <= limit else text[:limit] + "...(truncated)"


def _f(val: Optional[float]) -> str:
    if val is None: return ""
    return f"{val:.4f}"

# ---------------------------------------------------------------------------
# 终端输出
# ---------------------------------------------------------------------------
def _print_summary(summary: dict):
    """在终端打印汇总报告。"""
    logger.info("")
    logger.info("╔════════════════════════════════════════════╗")
    logger.info("║          RAG 评 估 汇 总 报 告            ║")
    logger.info("╠════════════════════════════════════════════╣")
    logger.info("║  总用例:     %5d                       ║", summary["total"])
    logger.info("║  成功/失败:  %5d / %-5d  (%.1f%%)     ║",
                summary["success_count"], summary["failed_count"], summary["success_rate"])
    logger.info("║  评估方式:   %s                          ║", summary.get("evaluation_method", "N/A"))
    logger.info("╠════════════════════════════════════════════╣")

    lat = summary.get("latency_ms", {})
    if lat:
        logger.info("║  [耗时]  avg=%.0fms  min=%.0fms  max=%.0fms ║",
                    lat.get("avg", 0), lat.get("min", 0), lat.get("max", 0))

    for metric, label in [
        ("answer_relevancy", "答案相关性"),
        ("faithfulness", "忠实度"),
        ("context_precision", "上下文精确度"),
        ("context_recall", "上下文召回率"),
        ("keyword_overlap", "关键词重叠度(Basic)"),
        ("context_answer_overlap", "C-A重叠度(Basic)"),
    ]:
        m = summary.get(metric, {})
        if m and m.get("count", 0) > 0:
            logger.info("║  [%s]  avg=%.4f  count=%d",
                        label, m.get("avg", 0), m.get("count", 0))

    logger.info("╚════════════════════════════════════════════╝")

    fb = summary.get("failure_breakdown", {})
    if fb:
        logger.info("失败原因分布: %s",
                     ", ".join(f"{k}={v}" for k, v in sorted(fb.items(), key=lambda x: -x[1])))

    kb = summary.get("by_kb_id")
    if kb:
        logger.info("按知识库统计:")
        for kbid, ks in kb.items():
            logger.info("  %s → 总计=%d 成功=%d 失败=%d 平均耗时=%.0fms",
                        kbid, ks["total"], ks["success"], ks["failed"], ks["avg_latency_ms"])


def _print_sample_contexts(results: List[EvalResult], num: int = 3):
    """打印部分成功用例的召回上下文，方便人工检查检索质量。"""
    success = [r for r in results if r.status == "success" and r.contexts]
    if not success:
        return

    show = success[:num]
    logger.info("")
    logger.info("╔════════════════════════════════════════════╗")
    logger.info("║       召 回 上 下 文 抽 样 展 示         ║")
    logger.info("╚════════════════════════════════════════════╝")

    for i, r in enumerate(show, 1):
        q = textwrap.shorten(r.question, width=80, placeholder="...")
        logger.info("")
        logger.info("── 样例 %d ─────────────────────────────────", i)
        logger.info("  [%s] 问题: %s", r.case_id, q)
        logger.info("  答案: %s", textwrap.shorten(r.answer or "(空)", width=120, placeholder="..."))
        logger.info("  召回上下文 (%d 条):", len(r.contexts))
        for j, ctx in enumerate(r.contexts, 1):
            ctx_short = textwrap.shorten(ctx.strip(), width=150, placeholder="...")
            logger.info("    [%d] %s", j, ctx_short)
        # 显示评估分数
        if r.scores:
            s = r.scores
            parts = []
            if s.context_precision is not None:
                parts.append(f"上下文精确度={s.context_precision:.4f}")
            if s.context_recall is not None:
                parts.append(f"上下文召回率={s.context_recall:.4f}")
            if s.answer_relevancy is not None:
                parts.append(f"答案相关性={s.answer_relevancy:.4f}")
            if s.faithfulness is not None:
                parts.append(f"忠实度={s.faithfulness:.4f}")
            if parts:
                logger.info("  评分: %s", " | ".join(parts))
    logger.info("")

# ---------------------------------------------------------------------------
# CLI
# ---------------------------------------------------------------------------
def parse_args():
    p = argparse.ArgumentParser(
        description="RAG 系统自动化测试与评估脚本",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=textwrap.dedent("""\
            示例:
              python rag_evaluation_tester.py -d test_dataset.json
              python rag_evaluation_tester.py -d test_dataset.json --ragas
              python rag_evaluation_tester.py -d test_dataset.json --ragas \\
                  --ragas-base-url https://api.deepseek.com/v1 \\
                  --ragas-api-key sk-xxxxx --ragas-model deepseek-chat
        """),
    )

    # --- 数据 & API ---
    p.add_argument("-d", "--data", required=True,
                   help="测试数据文件路径 (.json 或 .csv)")
    p.add_argument("-u", "--url",
                   default="http://localhost:8080/api/rag/evaluate/run",
                   help="RAG API 地址 (默认: %(default)s)")
    p.add_argument("-k", "--kb-id", default=None,
                   help="全局知识库 ID（会被用例级别的 kbId 覆盖）")

    # --- 并发 & 超时 ---
    p.add_argument("-w", "--max-workers", type=int, default=4,
                   help="并发线程数 (默认: %(default)s)")
    p.add_argument("-t", "--timeout", type=int, default=60,
                   help="单次请求超时秒数 (默认: %(default)s)")

    # --- RAGAS ---
    p.add_argument("--ragas", action="store_true",
                   help="启用 RAGAS LLM 评估（需 pip install ragas langchain-openai）")
    p.add_argument("--ragas-base-url", default="https://api.deepseek.com/v1",
                   help="RAGAS LLM 的 API Base URL（兼容 OpenAI 协议）")
    p.add_argument("--ragas-api-key", default=None,
                   help="RAGAS LLM 的 API Key（也可用环境变量 RAGAS_API_KEY）")
    p.add_argument("--ragas-model", default="deepseek-chat",
                   help="RAGAS 评估用模型名 (默认: %(default)s)")

    # --- 输出 ---
    p.add_argument("--csv-output", default="evaluation_report.csv",
                   help="CSV 报告路径 (默认: %(default)s)")
    p.add_argument("--json-output", default="evaluation_summary.json",
                   help="JSON 汇总路径 (默认: %(default)s)")

    # --- 其他 ---
    p.add_argument("--log-level", default="INFO",
                   choices=["DEBUG", "INFO", "WARNING", "ERROR"])
    p.add_argument("--dry-run", action="store_true",
                   help="仅加载数据，不发请求")

    return p.parse_args()

# ---------------------------------------------------------------------------
# 入口
# ---------------------------------------------------------------------------
def main():
    args = parse_args()
    logging.getLogger().setLevel(getattr(logging, args.log_level))

    # 1. 加载数据
    try:
        cases = load_test_data(args.data)
    except (FileNotFoundError, ValueError, json.JSONDecodeError) as e:
        logger.error("数据加载失败: %s", e)
        sys.exit(1)

    if not cases:
        logger.warning("无有效用例，退出")
        sys.exit(0)

    logger.info("数据预览（前 3 条）:")
    for tc in cases[:3]:
        logger.info("  [%s] kbId=%s  Q: %s", tc.id, tc.kb_id, tc.question[:80])

    if args.dry_run:
        logger.info("Dry-run 完成，共 %d 条用例（未发请求）", len(cases))
        sys.exit(0)

    # 2. 初始化评估器（在校验阶段就发现问题，避免白白发请求）
    ragas_eval = None
    if args.ragas:
        api_key = args.ragas_api_key or os.environ.get("RAGAS_API_KEY")
        if not api_key:
            logger.error(
                "启用 --ragas 但未提供 API Key。请通过 --ragas-api-key 或"
                " 环境变量 RAGAS_API_KEY 指定"
            )
            sys.exit(1)
        try:
            ragas_eval = RagasEvaluator(
                openai_api_base=args.ragas_base_url,
                openai_api_key=api_key,
                model=args.ragas_model,
            )
            logger.info("RAGAS 评估器初始化成功 (模型=%s, base_url=%s)",
                        args.ragas_model, args.ragas_base_url)
        except Exception as e:
            logger.error("RAGAS 初始化失败: %s", e)
            sys.exit(1)

    # 3. 并发请求
    results, raw_pairs = run_requests(cases, args.url, args.max_workers, args.timeout, args.kb_id)

    results = run_evaluation_phase(results, raw_pairs, cases, ragas_eval)

    # 4. 汇总
    summary = compute_summary(results, with_ragas=(ragas_eval is not None))

    # 5. 输出
    _print_summary(summary)
    _print_sample_contexts(results, num=3)
    export_csv(results, args.csv_output)
    export_json_summary(results, summary, args.json_output)

    sys.exit(1 if summary.get("failed_count", 0) > 0 else 0)


if __name__ == "__main__":
    main()
