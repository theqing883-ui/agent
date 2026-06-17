import { Server } from "@modelcontextprotocol/sdk/server/index.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import {
    CallToolRequestSchema,
    ListToolsRequestSchema,
} from "@modelcontextprotocol/sdk/types.js";

// 从环境变量读取配置 (由 Spring Boot 传入)
const API_KEY = process.env.BOCHA_API_KEY;
const ENDPOINT = process.env.BOCHA_ENDPOINT || "https://api.bochaai.com/v1/web-search";

if (!API_KEY) {
    console.error("Missing BOCHA_API_KEY environment variable");
    process.exit(1);
}

const server = new Server(
    { name: "bocha-search-server", version: "1.0.0" },
    { capabilities: { tools: {} } }
);

// 1. 告诉大模型我们有什么工具
server.setRequestHandler(ListToolsRequestSchema, async () => {
    return {
        tools: [
            {
                name: "bocha_web_search",
                description: "使用博查 API 进行实时联网搜索",
                inputSchema: {
                    type: "object",
                    properties: {
                        query: { type: "string", description: "搜索关键词" }
                    },
                    required: ["query"],
                },
            },
        ],
    };
});

// 2. 收到调用请求时，执行实际的 HTTP 请求
server.setRequestHandler(CallToolRequestSchema, async (request) => {
    if (request.params.name !== "bocha_web_search") {
        throw new Error(`Unknown tool: ${request.params.name}`);
    }

    const query = request.params.arguments?.query;
    if (!query) {
        throw new Error("Missing query parameter");
    }

    try {
        const response = await fetch(ENDPOINT, {
            method: "POST",
            headers: {
                "Content-Type": "application/json",
                "Authorization": `Bearer ${API_KEY}`,
            },
            body: JSON.stringify({
                query: query,
                freshness: "noLimit",
                summary: true,
                count: 8,
            }),
        });

        if (!response.ok) {
            throw new Error(`Bocha API returned status: ${response.status}`);
        }

        const data = await response.json();

        // 解析博查的返回结果
        let resultText = "搜索成功，但未找到相关内容。";
        if (data && data.data && data.data.webPages && data.data.webPages.value) {
            // 直接将搜索结果转为字符串返给大模型
            resultText = "来自博查的搜索结果：\n" + JSON.stringify(data.data.webPages.value);
        }

        return {
            content: [
                {
                    type: "text",
                    text: resultText,
                },
            ],
        };
    } catch (error) {
        return {
            content: [
                {
                    type: "text",
                    text: `Error: 联网搜索请求失败 - ${error.message}`,
                },
            ],
            isError: true,
        };
    }
});

// 3. 启动并监听标准输入输出 (Stdio)
const transport = new StdioServerTransport();
server.connect(transport).catch(console.error);