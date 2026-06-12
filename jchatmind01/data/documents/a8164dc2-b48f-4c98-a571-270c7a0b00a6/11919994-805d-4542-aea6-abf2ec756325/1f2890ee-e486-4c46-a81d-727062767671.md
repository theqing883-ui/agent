<!-- 2026年4月 -->

<!-- 西北工业大学学报 -->

<!-- Apr. 2026 -->

<!-- 第44卷第2期 -->

<!-- Journal of Northwestern Polytechnical University -->

<!-- Vol.44 No.2 -->

**https://doi.org/10.1051/jnwpu/20264420339**

# MOAK：一种面向相控阵天线机电耦合设计的多目标代理模型优化算法

于驰，胡祥涛

（安徽大学 电气工程与自动化学院，安徽合肥 230000）

摘要：为减小相控阵天线质量、降低制造成本，并保障恶劣工况下辐射性能稳定性，提出了一种基于Kriging模型的多目标代理优化算法-MOAK。该算法融合可行域探索准则和TCF-SMOTE准则形成组合加点准则，通过可行域探索准则实现全域可行空间的有效辨识，进而利用创新设计的TCF-SMOTE准则构建完整Pareto前沿，实现高精度Pareto最优解集求解。其中，TCF-SMOTE准则通过截断约束函数（TCF）抑制Kriging模型构建中的极端违约值，规避可行域边界模型失真；同时，应用SMOTE算法生成合成样本点，增加Pareto解集（PS）数量，提升Pareto前沿（PF）完整性。在3个标准测试函数的对比试验中，MOAK算法展现出了显著优势，改善了常规代理模型优化算法中PS稀疏、PF 刻画不完整的问题。将文中所提算法应用于相控阵天线前骨架优化设计，MOAK在结构轻量化、成本和辐射性能平衡方面提供了多样性解决方案，满足不同设计偏好需求。其中，典型优化方案在保持辐射性能稳定的前提下，实现了质量减小35.4%，成本降低80.2%，计算时长缩减84.91％的综合效益。

**关键词：**Kriging；代理模型；加点准则；多目标优化；相控阵天线

**中图分类号**：TN821.8

**文献标志码：A**

**文章编号**：1000-2758(2026)02-0339-12

雷达是典型的以“电”为目标、以“机械”为载体的综合集成装备，是现代信息战争的主要装备之一。天线阵面是相控阵雷达主要功能部件，其结构不仅是电性能实现的载体和保障，而且制约着电性能的实现［1］。随着雷达对高速目标、隐身目标、多目标的探测跟踪性能及自身机动性的要求日益提高，雷达天线阵面呈现出大型化发展趋势。天线骨架作为主承力结构件，工程上对其刚度、质量、尺寸等结构性能提出了严格要求，以确保雷达在恶劣环境条件下稳定工作［2］。因此，天线骨架结构设计的优劣直接关系到雷达系统辐射性能。常规的设计主要采用经验设计方法，借鉴已有的成熟框架结构形式进行设计［3］。这种经验设计方法，尽管能够保证结构刚度，但通常不是最优结构，会带来系统质量增大、成本过高等问题。

**收稿日期**：2025-07-05

**基金项目**：国家自然科学基金（52175210）资助

**作者简介**：于驰（2000-)，硕士研究生

**通信作者**：胡祥涛（1981-)，教授 e-mail:husthoo@ahu.edu.cn

随着数值计算技术的不断成熟，基于群智能优化算法的设计方法常被用于解决复杂结构优化设计难题。然而，这类方法需要频繁调用数值计算模型，存在优化效率低下的问题。为此，代理模型优化方法逐渐兴起，通过构建代理模型以替代物理实验或数值计算，从而提升优化效率［4］。代理模型主要包括多项式响应面模型、Kriging模型、径向基函数模型、支持向量回归模型等［5]，其中，Kriging模型能够同时提供任意试验点上的预测值及预测误差，被广泛应用于工程优化领域。尤其在单目标优化设计问题中，Kriging模型已展现出显著的技术优势与工程实用性［6-10］。

然而，实际工程优化问题通常涉及多个相互冲突的优化目标，并受到复杂约束条件的限制。因此，如何将Kriging模型推广到多目标优化问题（multi-objective optimization problems,MOPs）中，近年来引起了学术界的广泛关注和重视。作为代理模型优化的核心环节，自适应加点准则的设计至关重要，已有众多学者针对MOPs提出了一系列专用加点策略。Emmerich等 $[11]$ 基于超体积（hyper volume,HV）质量指标提出了期望超体积改进（expected hypervolume improvement,EHI）准则，解决了MOPs优化效率低下的问题。针对约束优化问题，MartíNez等［12］引入了可行性概率（probability of feasibility,PoF)，提出了期望可行超体积改进（expected feasible hypervolume improvement,EFI）准则。张建侠等［13］结合EFI和可行域探索准则，设计了组合加点准则，提出了GCMOA算法。然而，现有加点准则在处理MOPs时仍普遍存在以下局限：PoF评估不准确导致试验设计（design of experiments,DoE）出现严重违反约束的情况，引发Kriging模型的全局失真；问题非线性增强加剧Pareto解集稀疏化，导致前沿刻画不完整。

<!-- 万方数据 -->

<!-- ·340· 西北工业大学学报 第44卷 -->

针对上述问题，本文提出了一种基于Kriging模型的多目标代理优化算法-MOAK算法。该算法通过创新设计的组合加点准则实现高精度Pareto最优解集求解。组合加点准则主要包括两部分：可行域探索准则和TCF-SMOTE准则。MOAK算法首先采用可行域探索准则全局定位设计空间中的可行区域，然后利用TCF-SMOTE准则，构建完整Pareto 前沿。

## 1 Kriging模型及其加点准则概述

### 1.1 MOPs数学描述

MOPs是指在1个优化问题中，包含m个优化目标和n个约束条件。其定义可以表示为

$$\min _{x}:y(x)=(y_{1}(x),y_{2}(x),\cdots ,y_{m}(x))$$

$$\text {s.t.}:g_{i}(x)\leq 0,\quad i=1,2,\cdots ,n\tag{1}$$

式中： $y(x)$  表示目标函数向量；x表示设计变量； $g_{i}(x)$ 表示第i个约束条件。

在MOPs中，由于各目标函数之间的冲突性，通常不存在单一的全局最优解，而是存在1组由Pareto 最优解组成的集合（Pareto solutions,PS）。这些Pareto最优解经过目标函数映射到目标空间后，就构成了所谓的Pareto前沿（Pareto front,F $\mathrm {PF})^{[14]}$ O 零、方差为 $σ_{Z}^{2}$  的随机分布误差 $\beta$  表示回归系数。

### 1.2 Kriging 模型

Kriging模型是一种基于统计理论的插值技术，由一个回归部分和一个偏差部分组成。

$$y(x)=f^{T}(x)\beta +Z(x)\tag{2}$$

式中： $f(x)$ 表示多项式回归函数； $Z(x)$ 代表均值为

回归系数 $\beta$  和方差 $σ_{Z}^{2}$ 按（3)~(4）式进行计算。

$$\boldsymbol {\beta }=(\boldsymbol {X}^{\mathrm {T}}\boldsymbol {R}^{-1}\boldsymbol {X})^{-1}\boldsymbol {X}^{\mathrm {T}}\boldsymbol {R}^{-1}\boldsymbol {y}\tag{3}$$

$$\sigma _{Z}^{2}=\frac {1}{n}(\boldsymbol {y}-\boldsymbol {X}\boldsymbol {\beta })^{\mathrm {T}}\boldsymbol {R}^{-1}(\boldsymbol {y}-\boldsymbol {X}\boldsymbol {\beta })\tag{4}$$

式中， $X=[f(x_{1}),f(x_{2}),\cdots ,f(x_{n})]^{T},y$ 表示样本响应向量；R为样本点之间相关矩阵。

Kriging模型在未知点 $x^{*}$  ”的预测均值以及预测方差可以表达为

$$\hat {y}(x^{*})=f^{T}(x^{*})\beta +r^{T}(x^{*})R^{-1}(y-X\beta )\tag{5}$$

$S_{\hat {y}}^{2}(\boldsymbol {x}^{*})=\sigma _{Z}^{2}(1-\boldsymbol {r}^{\mathrm {T}}\boldsymbol {R}^{-1}\boldsymbol {r}+\boldsymbol {u}^{\mathrm {T}}\left(\boldsymbol {X}^{\mathrm {T}}\boldsymbol {R}^{-1}\boldsymbol {X}\right)^{-1}\boldsymbol {u})$  (6)式中， $u=X^{T}R^{-1}r(x^{*})-f(x^{*}),r(x^{*})$ 表示预测点和样本点之间的相关向量。

本文选取常用的高斯函数作为相关函数，零阶多项式作为回归模型。

### 1.3 自适应加点准则

自适应加点准则是代理模型优化中的一项重要优化迭代策略，通过生成最有可能改进当前近似PF 的候选样本点，逐步构建完整的PF。在当前基于Kriging模型的多目标代理优化算法中，自适应加点准则主要有2种。

#### 1)EHI准则

根据文献［11],EHI准则表示为

$$E_{\mathrm {HI}}\left(\boldsymbol {x},P_{\mathrm {F}},\boldsymbol {r}\right)=\int _{\hat {f}\in \mathbf {C}}H\left(\hat {\boldsymbol {y}}(\boldsymbol {x}),P_{\mathrm {F}},\boldsymbol {r}\right)·\varphi _{x}(\hat {\boldsymbol {y}})\mathrm {d}\hat {\boldsymbol {y}}\tag{7}$$

$$H\left(\boldsymbol {y}\left(\boldsymbol {x}_{\mathrm {c}}\right),P_{\mathrm {F}},\boldsymbol {r}\right)=$$

$$\left\{\begin{matrix}HV(P_{F}\cup y(x_{c}),r),y(x_{c})<P_{F}-HV(P_{F},r)\\ 0,其他\end{matrix}\right.$$

(8)

式中： $x_{c}$ 为新添加试验点； $C\subset \mathbf {R}^{m}$ 是由参考点r界定的非支配域； $\varphi _{x}(\hat {y})$ 是目标函数的联合概率密度。 $E_{\mathrm {HI}}$ 取值越大，说明新样本点对现有近似PF（用 $P_{\mathrm {F}}$ 表示）的改进也越大。

#### 2)EFI准则

针对含约束的多目标优化场景，文献［12］提出EFI准则，将EHI与样本点位于可行域的概率 $P(x)$ 相结合

$$E_{\mathrm {FI}}\left(\boldsymbol {x},P_{\mathrm {F}},\boldsymbol {r}\right)=P(\boldsymbol {x})\times E_{\mathrm {HI}}\left(\boldsymbol {x},P_{\mathrm {F}},\boldsymbol {r}\right)\tag{9}$$

$$P(x)=\prod _{i=1}^{n}\text {Pr}(g_{i}(x)\leq 0)=\prod _{i=1}^{n}\Phi (\frac {-\hat {g}_{i}(x)}{\sigma _{\hat {g}_{i}}(x)})\tag{10}$$

式中，Pr是概率度量； $\hat {g}_{i}(x)$ 和 $\boldsymbol {σ}_{gi}(\boldsymbol {x})$ 分别是第i个约束的预测均值和标准差； $Φ$  是标准高斯分布函数。

<!-- 万方数据 -->

<!-- 第2期 于驰，等：MOAK：一种面向相控阵天线机电耦合设计的多目标代理模型优化算法 ·341· -->

通过最大化 $E_{\mathrm {FI}}$ 函数，可筛选出既满足约束要求又能显著改进近似 $P_{\mathrm {F}}$ 的样本点，作为下一个的最佳采样点。

## 2 MOAK算法

尽管现有加点准则被证明能有效应对多目标约束优化问题，但在处理复杂MOPs时，仍然存在因PoF预测的不稳定而陷入寻优困境，导致PS数量不足以及PF刻画不清晰的问题。为此，本文提出一种融合可行域探索准则与TCF-SMOTE准则的组合加点准则，以实现高精度PF的近似构建。该策略通过可行域探索准则全局定位优化空间中的有效解区域，结合TCF-SMOTE准则的TCF机制抑制边界样本失真，并利用SMOTE机制的插值特性扩充PS 数量。从而保证解的可行性，同时显著提升Pareto 前沿的收敛精度与完整性。

### 2.1 可行域探索准则

当优化问题的可行域呈现小尺度特征，或包含多个非连通子区域时，单纯采用EFI准则进行寻优加点，极易陷入局部最优陷阱，导致优化效率与精度显著下降。为此，本文采用可行域探索准则 $[13]$ ，精准地识别所有非连通可行区域。

可行域探索准则公式化表述为

$$x=\text {argmax}\left(C_{fea}(x)\right)\tag{11}$$

$$C_{\mathrm {fea}}(x)=(D(x))^{q}\prod _{i=1}^{n}\sigma _{\hat {g}_{i}}(x)\cdot \text {Pr}(\hat {g}_{i}(x)\leq 0)$$

(12)

$$D(x)=\min _{x_{fea}}(\vert x-x_{fea}\vert )\tag{13}$$

式中： $C_{\mathrm {fea}}$ 为可行域探索函数； $\boldsymbol {x}_{\mathrm {fea}}$  表示现有可行试验点； $q\geq 0$ 是用以控制探索能力的参数。

通过（13）式寻找新样本点 $x$  ，能够系统且精准地识别所有潜在可行区域，有效解决可行域非连通、结构复杂的难题，确保优化过程覆盖全局解空间。

### 2.2 TCF-SMOTE准则

在可行域探索准则基础上，本文提出TCF-SMOTE准则，进一步生成更多的PS，构建更完整的PF。TCF-SMOTE准则主要由2个机制组成：

1)TCF 机制：通过借鉴截断约束函数(truncated constraint function $TCF)[15]$ ]，限制Kriging 模型DoE中出现极度违反约束的情况，以减弱PoF 不稳定性带来的影响。

2)SMOTE机制：在获得近似PF轮廓后，采用SMOTE(synthetic minority oversampling technique）算法进行过采样，增加PS数量并获得完整PF。

#### 2.2.1 TCF机制

TCF机制通过引入PoF下限 $\left(P_{u}\right)$ 避免 Kriging 模型在可行域边界的失真。TCF机制工作原理为：首先设定 $P_{\mathrm {u}}$ ，按照（14）式生成待选样本集Ω，然后根据 $P_{\mathrm {u}}$ 获得截断集 $\boldsymbol {Ω}_{\mathrm {T}}\left(\boldsymbol {x}:P(\boldsymbol {x})>P_{\mathrm {u}}\right)$ ，再按照（15）式确定新添样本点 $x^{*}$ 。重复上述过程 $N_{\mathrm {val}}$ 次，得到新添样本集 $W(x^{*}\vert P_{u})$ 

$$\boldsymbol {x}=\text {argmax}\left(E_{\mathrm {HI}}\left(\boldsymbol {x},P_{\mathrm {F}},\boldsymbol {r}\right),P(\boldsymbol {x})\right)\tag{14}$$

$$\boldsymbol {x}^{*}=\text {argmax}_{\boldsymbol {x}\in \boldsymbol {\Omega }_{\mathrm {T}}}\left(E_{\mathrm {HI}}\left(\boldsymbol {x},P_{\mathrm {F}},\boldsymbol {r}\right)\times P(\boldsymbol {x})\right)\tag{15}$$

为了确定最优的 $P_{\mathrm {u}}$ ，定义了Kriging模型在可行域边界的失真率函数

$$M(P_{u})=\sum _{x^{*}\in W}\frac {\text {Index}\left(x^{*}|P_{u}\right)}{N_{\mathrm {val}}}\tag{16}$$

式中：M是在给定 $P_{\mathrm {u}}$ 下的Kriging模型失真率；Index 为指示函数，定义如（17）式所示。

$$Index(x^{*}\vert P_{u})=\{\begin{matrix}-1,&g(x^{*}\in W)\leq g_{c}\\ 1,&\text {其他}\end{matrix}$$

(17)

式中：g为约束函数集 $;g_{\mathrm {c}}$ 为各约束函数可接受上限。通过最小化M寻找 $P_{\mathrm {u}\text {-best}}$ ，如（18）式所示。

$$\min :M\left(P_{\mathrm {u}}\right)\tag{18}$$

在 $P_{\mathrm {u}\text {-best}}$  截断下，获得新添样本点集 $\boldsymbol {W}_{b}\left(\boldsymbol {x}^{*}|P_{\text {u-best}}\right)_{\text {o}}$ 

#### 2.2.2 SMOTE 机制

SMOTE作为一种经典的合成过抽样算法，通过在少数类样本的特征空间中生成新样本，解决可靠性优化中数据不平衡问题 $[16]$ 。受此机制启发，本文在通过TCF机制完成样本填充后，将近似PF上的样本定义为少数类集合，借助过采样技术对其进行插值扩充。

SMOTE采样分为2步。首先构建备选样本集。将近似PF上的样本点记为 $\boldsymbol {x}_{i},i\in \{1,2,\cdots ,M\}$ ；设定向上采样倍率为N，对于每个 $\boldsymbol {x}_{i}$ ，从其PF样本集K 近邻上随机选取样本点 $\hat {\boldsymbol {x}}_{j},j\in \{1,2,\cdots ,N\}$ ；按照（19）式生成新的样本 $x_{\text {new}}(i,j)。$ 

$$x_{new}(i,j)=x_{i}+\text {rand}(0,1)\cdot (\hat {x}_{j}-x_{i})\tag{19}$$

其次，对于新生成样本按（9）式计算其EFI值，该指标量化了其对当前帕累托前沿的优化贡献。随后，将所有新样本依据EFI值从高到低进行排序，选取排序靠前的 $N_{\text {new}}$ 个样本，将其纳入DoE中，从而

<!-- 万方数据 -->

<!-- ·342· 西北工业大学学报 第44卷 -->

<!-- 万方数据 -->

完成样本库的更新与优化。

### 2.3 MOAK算法

图1给出了基于探索可行域准则与TCF-SMOTE准则的MOAK算法流程图。具体实现步骤如下：

1）定义MOAK算法的初始参数：初始试验样本数量 $N_{\mathrm {um}},\mathrm {PS}$ 集数量 $N_{\mathrm {PS}}$ 判断参数 $N_{\mathrm {PG}}$ ，当前样本数量 $N_{\mathrm {p}}$ ，以及SMOTE生成样本数量 $N_{\text {new}}$ 等。

2）在设计空间中通过拉丁超立方体进行采样，对每个样本进行仿真得到真实响应值，生成初始样本，建立初始Kriging模型。

3）检验PS集数量是否大于 $N_{\mathrm {PS}}$ ，如果为否，利用可行域探索准则选取新试验点；如果为是，生成初始近似PF，进入下一步骤。

4）判断 $N_{\mathrm {P}}$ 是否大于 $N_{\mathrm {PG}}$ ，如果为否，利用TCF 准则选取新试验点，进行近似PF轮廓刻画；如果为是，利用SMOTE准则进行加点以完善近似PF。

5）更新试验数据，获得最终近似PF。

<!-- MOAK算法初始化 参数初始化 拉丁超立方抽样 仿真得到真实响应值 建立初始Kriging模型 探索可行域 TCF-SMOTE PS集是否满足 是 由可行试验点集生成 条件 初始近似PF 否 利用探索可行域准则 选取新试验点 $N_{\mathrm {P}}&gt;N_{\mathrm {PG}}$ 否 更新试验数据以及 利用TCF准则 Kriging 模型 选取新试验点 $N_{\mathrm {P}}=N_{\mathrm {P}}+1$ 是 更新试验数据以及 Kriging模型 利用SMOTE准则 选取新试验点 更新试验数据，生成 最终近似PF -->
![](https://web-api.textin.com/ocr_image/external/877d349735eec7d7.jpg)

图1 MOAK算法流程图

## 3 算例验证

### 3.1 标准测试函数

为了评估MOAK算法的有效性和稳定性，本节选取3个复杂程度各异的标准测试函数进行实验，并将优化结果与GCMOA算法 $[13]$ 进行对比。

1）测试函数1

$\left.c_{2}-15\right)^{2}+10$ $\left\{\begin{array}{l}\min :f_{1}(\boldsymbol{x})=\left(x_{1}-10\right)^{2}+\left(x_{2}-15\right)^{2}\\
\min :f_{2}(\boldsymbol{x})=\left(x_{1}+5\right)^{2}+x_{2}^{2}+10\\
g(\boldsymbol{x})=\left(x_{2}-\frac{5}{4\pi^{2}}x_{1}^{2}+\frac{5}{\pi}x_{1}-6\right)^{2}+\end{array}\right.$ $+7\leq 0$ 

(20)

式中： $x_{1}\in [-5,10];x_{2}\in [0,15]$ 

#### 2）测试函数2

$$\min :f_{1}(x)=\sum _{i=1}^{7}x_{i}^{2}+\sin (\sum _{i=1}^{7}x_{i})+\quad \sqrt {1+\sum _{i=1}^{7}\left(x_{i}-1\right)^{2}}+\ln \left(1+\sum _{i=1}^{7}x_{i}\right)\quad \min :f_{2}(x)=\sum _{i=1}^{7}(x_{i}-1)^{2}+\cos (\sum _{i=1}^{7}(x_{i}-1))+\quad \exp \left(\sum _{i=1}^{7}\left(x_{i}-1\right)\right)+\tanh \left(\sum _{i=1}^{7}\left(x_{i}-1\right)\right)\quad \left|\max _{\textbf {:}}f_{3}(\boldsymbol {x})=\sum _{i=1}^{7}\left(x_{i}+1\right)^{2}+\tanh \left(\sum _{i=1}^{7}\left(x_{i}+1\right)\right)+\right|\quad \sqrt {1+\sum _{i=1}^{7}(x_{i}+1)^{2}}+\ln (1+\sum _{i=1}^{7}(x_{i}+1))\quad g_{1}(x)=\sum _{i=1}^{7}x_{i}^{2}-100\leq 0\quad g_{2}(x)=\sum _{i=1}^{7}(x_{i}-1)^{2}-90\leq 0\quad \left|g_{{}_{3}}(\boldsymbol {x})=\sum _{{}_{i=1}}^{7}\left(x_{{}_{i}}+1\right)^{2}-80\leq 0\right|\quad g_{4}(x)=\sum _{i=1}^{7}\left(x_{i}-2\right)^{2}-70\leq 0$$

(21)

式中， $x_{1},x_{2},\cdots ,x_{7}\in [1,7]$ 

<!-- 第2期 -->

<!-- 于驰，等：MOAK：一种面向相控阵天线机电耦合设计的多目标代理模型优化算法 -->

<!-- ·343· -->

<!-- 万方数据 -->

#### 3）测试函数3

$(\min :f_{1}(x)=-(25(x_{1}-2)^{2}+(x_{2}-2)^{2}+$ $(x_{3}-1)^{2}+(x_{4}-4)^{2}+(x_{5}-1)^{2}$ min: $f_{2}(x)=x_{1}^{2}+x_{2}^{2}+x_{3}^{2}+x_{4}^{2}+x_{5}^{2}$ $g_{1}(x)=2-x_{1}-x_{2}\leq 0$ $g_{2}(\boldsymbol {x})=x_{{}_{1}}+x_{{}_{2}}-6\leq 0.$ $\vert g_{{}_{3}}(\boldsymbol {x})=x_{{}_{2}}-x_{{}_{1}}-2\leq 0\vert$ $\left|g_{{}_{4}}(\boldsymbol {x})=x_{{}_{1}}-3x_{{}_{2}}-2\leq 0\right|$ $\vert g_{5}(x)=(x_{3}-3)^{2}+x_{4}-4\leq 0\vert$ $g_{6}(x)=4-(x_{5}-3)^{2}-x_{6}\leq 0$ 

(22)

式中： $x_{1},$ $x_{2}$  $x_{6}\in [-5,10]$  $x_{3},x_{5}\in [1,5]$ ; $x_{4}\in [0,6]$ 

### 3.2 评价指标与参数设置

以NSGA-III算法（初始种群为200，迭代次数为100）为基准，将其求得的近似PF和PS分别作为参考PF（用 $P_{\text {Fref}}$ 表示）和参考PS集（用 $\boldsymbol {P}_{\text {Sref}}$ 表示）。选取PS数量、代数距离（generational distance,GD）以及相对超体积（relative hyper-volume,RHV）作为评估指标 $[17]$ 。

GD用于度量Kriging模型生成的近似PS集（用 $\boldsymbol {P}_{\text {Sest}}$ 表示）与 $\boldsymbol {P}_{\text {Sref}}$ $之$ 间的距离，GD值越小，表明算法的收敛性越好。GD计算公式为

$$Y_{\mathrm {GD}}\left(\boldsymbol {P}_{\mathrm {Sest}},\boldsymbol {P}_{\mathrm {Sref}}\right)=\frac {\sqrt {\sum _{\boldsymbol {y}\in \boldsymbol {P}_{\mathrm {Sest}}}\min _{\boldsymbol {y}_{\mathrm {ref}}\in \boldsymbol {P}_{\mathrm {Sref}}}d\left(\boldsymbol {y},\boldsymbol {y}_{\mathrm {ref}}\right)^{2}}}{N_{P_{\mathrm {Sest}}}}$$

(23)

式中： $d\left(\boldsymbol {y},\boldsymbol {y}_{\mathrm {ref}}\right)$ 表示 $\boldsymbol {P}_{\text {Sest}}$ 中y和 $\boldsymbol {P}_{\text {Sref}}$ 中 $y_{\mathrm {ref}}之$ 间的欧式距离； $;N_{P_{\text {Sest}}}$ 表示 $\boldsymbol {P}_{\text {Sest}}$ 集中PS数量。

RHV则用于表示 Kriging模型生成的近似PF（用 $\boldsymbol {P}_{\text {Fest}}$ 表示）相对于 $\boldsymbol {P}_{\text {Fref}}$ 的超体积偏差，即

$$Y_{\mathrm {RHV}}\left(\boldsymbol {P}_{\mathrm {Fest}}\right)=1-\frac {\mathrm {HV}\left(\boldsymbol {P}_{\mathrm {Fest}}\right)}{\mathrm {HV}\left(\boldsymbol {P}_{\mathrm {Fref}}\right)}\tag{24}$$

RHV可同时用于评估算法的收敛性与多样性，值越小表明算法优化性能越好。MOAK算法参数见表1。

**表1 MOAK算法参数**

<table border="1" ><tr>
<td>测试函数</td>
<td>$N_{\mathrm {PS}}$</td>
<td>q</td>
<td>$N_{\mathrm {PG}}$</td>
<td>$g_{\mathrm {c}}$</td>
<td>$N_{\text {new}}$</td>
<td>$N_{\mathrm {um}}$</td>
</tr><tr>
<td>1</td>
<td>10</td>
<td>1</td>
<td>70</td>
<td>5</td>
<td>10</td>
<td>20</td>
</tr><tr>
<td>2</td>
<td>40</td>
<td>1</td>
<td>200</td>
<td>5</td>
<td>50</td>
<td>70</td>
</tr><tr>
<td>3</td>
<td>10</td>
<td>1</td>
<td>250</td>
<td>5</td>
<td>50</td>
<td>60</td>
</tr></table>

30组试验结果的评价指标均值和标准差如表2所示，其中GCMOA的结果源于文献［13］。可以看出：MOAK算法生成的PS数量多于GCMOA算法，表明MOAK刻画PF能力更强；MOAK算法的GD 值小于GCMOA算法，表明MOAK算法计算结果更接近 $\boldsymbol {P}_{\text {Sref}}$ ，收敛性更好；RHV值也展现出了相似的规律，MOAK算法的HV更接近NSGA-III算法给出的参考HV。此外，3个性能指标的标准差结果也表明MOAK算法性能更稳定。综上，在3个标准测试函数的对比试验中，MOAK算法展现出了显著优势，尤其是对于复杂测试函数2和3，性能优势更为明显。

**表2 MOAK算法与GCMOA算法计算结果**

<table border="1" ><tr>
<td rowspan="2">测试函数</td>
<td rowspan="2"> 优化算法</td>
<td colspan="3">性能指标均值（标准差）</td>
</tr><tr>
<td>PS 数量</td>
<td>GD</td>
<td>RHV</td>
</tr><tr>
<td rowspan="2">1</td>
<td>GCMOA</td>
<td>31<br>(3.994 9)</td>
<td>0.275 7<br>(0.050 4)</td>
<td>0.008 18<br>(0.003 2)</td>
</tr><tr>
<td>MOAK</td>
<td>34<br>(3.8209)</td>
<td>0.2601<br>(0.045 5)</td>
<td>0.007 70<br>(0.003 1)</td>
</tr><tr>
<td rowspan="2">2</td>
<td>GCMOA</td>
<td>72<br>(3.346 6</td>
<td>6.269 7<br>)(1.179 1)</td>
<td>0.058 76<br>(0.013 2)</td>
</tr><tr>
<td>MOAK</td>
<td>118<br>(1.833 0)</td>
<td>3.900 9<br>(0.773 9)</td>
<td>0.025 24<br>(0.009 4)</td>
</tr><tr>
<td rowspan="2">3</td>
<td>GCMOA</td>
<td>8<br>(0.942 8)</td>
<td>6.062 9<br>(2.290 2)</td>
<td>0.097 9<br>(0.025 56)</td>
</tr><tr>
<td>MOAK</td>
<td>35<br>(0.745 3)</td>
<td>1.998 8<br>(1.0257)</td>
<td>0.049 5<br>(0.0227)</td>
</tr></table>

图2分别给出了3种测试函数的近似PF轮廓。可以观察到，对于测试函数1,MOAK算法与GCMOA算法均表现出较好的优化性能，其生成的近似PF与参考PF高度一致。然而，随着问题复杂度的增加（测试函数2和3),GCMOA算法开始出现PS集数量不足、PF轮廓刻画不完整的问题；相比之下，MOAK算法提供了足够数量的PS集，刻画出较为完整的PF轮廓。此外，在MOAK算法与NAGA-III算法的PF轮廓对比图中，也可以看出两者的PF 轮廓几乎完全吻合。这进一步表明了MOAK算法有效改善了常规代理模型优化算法中PS稀疏、PF 刻画不完整的问题。

<!-- ·344· -->

<!-- 西北工业大学学报 -->

<!-- 第44卷 -->

<!-- 260 ·GCMOA 220 180 $f_{2}$ 140 100 60 120 160 200 240 f $f_{1}$ -->
![](https://web-api.textin.com/ocr_image/external/c02e06098b0fd5ce.jpg)

<!-- 260 ·MOAK 220 180 $f_{2}$ 140 100 60 120 160 200 240 f $f_{1}$ -->
![](https://web-api.textin.com/ocr_image/external/df046a066fd0edf9.jpg)

<!-- 240 ·MOAK ·NSGA-III 200 $f_{2}$ 160 120 80 140 180 220 $f_{1}$ -->
![](https://web-api.textin.com/ocr_image/external/2e10d4c0d6d0543b.jpg)

a）测试函数1

<!-- 100 ·GCMOA 80 $f_{3}$ 60 40 10 60 40 $f_{2}/10^{3}$ 0 $20$ $f_{1}$ -->
![](https://web-api.textin.com/ocr_image/external/1b6e9370d41833ad.jpg)

<!-- 100 ·MOAK 80 $f_{3}$ 60 40 10 5 60 $f_{2}/10^{3}$ 40 0 $20$ $f_{1}$ -->
![](https://web-api.textin.com/ocr_image/external/5bc092cccebc4419.jpg)

<!-- 100 MOAK NSGA-III 80 $f_{3}$ 60 40 15 10 60 $f_{2}/10^{3}$ 5 40 0 20 f $f_{1}$ -->
![](https://web-api.textin.com/ocr_image/external/25a1b367a016f534.jpg)

b）测试函数2

<!-- 120 ·GCMOA 80 . 5 $f_{2}$ 40 . 0 $-26$ $-220$ $-180$ -140 -100 f $f_{1}$ -->
![](https://web-api.textin.com/ocr_image/external/a076782e5a81b6bb.jpg)

<!-- 100 : ·MOAK 80 60 : $f_{2}$ 40 : 20 0 -300 $-200$ $-100$ 0 $f_{1}$ f -->
![](https://web-api.textin.com/ocr_image/external/4692e5decdfb1666.jpg)

<!-- 100 ·MOAK ·NSGA-III 80 60 $f_{2}$ 40 20 0 $-30$ $-200$ $-100$ 0 $f_{1}$ -->
![](https://web-api.textin.com/ocr_image/external/15679ee9c2356507.jpg)

c）测试函数3

图2 3个测试函数近似PF轮廓

## 4 前骨架优化设计

本节针对某型号相控阵天线前骨架，构建了一个旨在减轻前骨架质量、降低成本并平衡辐射性能的多目标优化模型。利用本文提出的MOAK算法，在最大允许变形量和应力约束下进行工程实例优化设计。

### 4.1 前骨架结构参数及载荷

前骨架为箱体结构，主要由承力框架、反射板和盖板组成，如图3所示。为了进行优化设计，以反射

<!-- $X_{2}$ $X_{6}$ $X_{4}$ $X_{5}$ 0 0 e $X_{1}$ 0 0 0 e - e 0 $X_{3}$ 0 e -->
![](https://web-api.textin.com/ocr_image/external/760f49b5886e7d4f.jpg)

图3 前骨架结构示意图

<!-- 万方数据 -->

<!-- 第2期 -->

<!-- 于驰，等：MOAK：一种面向相控阵天线机电耦合设计的多目标代理模型优化算法 -->

<!-- ·345· -->

板厚度 $X_{1}$ 、侧壁厚度 $X_{2}$ 、长支撑板厚度 $X_{3}$ 、短支撑板厚度 $X_{4}$ 、底板厚度 $X_{5}$ 散热孔半径 $X_{6}$ 为设计变量进行参数化建模［18］。

天线单元工作频率为750 MHz，阵面采用 $8\times 8$ 阵列天线布局。阵面主要受到风载、内部组件和电源等设备重力载荷作用。根据设计要求，风载主要分为2种工况：①工况1，正常工作风速不大于 $25m/s$ ；②工况2，不破坏风速不大于 $65\mathrm {\;m}/\mathrm {s}_{\circ }$ 。风载直接作用在前骨架反射板上，按每种工况下最大风速进行计算。风载荷计算方法 $[19]$ 如（25)~(26）式所示。

$$W_{\mathrm {k}}=\beta _{\mathrm {z}}\times \mu _{\mathrm {s}}\times \mu _{\mathrm {z}}\times W_{0}\times H\times W\tag{25}$$

$$W_{0}=0.5\times r\times V^{2}/g\tag{26}$$

式中： $\beta _{\mathrm {z}}$ 为风振系数（建议取1.2） $;μ_{\mathrm {s}}$  为体型系数（建议取1.3） $;μ_{z}$ 为高度系数（建议取1.1)； $W_{0}$ 为基本风压；V为风速；g为重力加速度；r为空气重度（标准大气压下 $0.01225\mathrm {kN}/\mathrm {m}^{3}$ ）

### 4.2 多目标优化设计模型

将前骨架各薄壁件厚度以及零件材料种类 $X_{7}$ 为优化设计变量，即

$$\boldsymbol {X}=\left[X_{1},X_{2},X_{3},X_{4},X_{5},X_{6},X_{7}\right]^{\mathrm {T}}\tag{27}$$

常见的结构材料及其基本力学性能参数见表3，表中 $σ$  为屈服强度，p为密度，G为弹性模量， $P_{\mathrm {ri}}$ 为价格。

**表3 材料力学性能参数**

<table border="1" ><tr>
<td>材料种类$X_{7}$</td>
<td>σ/<br>$MPa$</td>
<td>$ρ/$ $\left(\mathrm {kg}·\mathrm {\;m}^{-3}\right)$</td>
<td>$G/GPa$</td>
<td>（元·$\left.\mathrm {kg}^{-1}\right)$</td>
</tr><tr>
<td>5A05</td>
<td>145</td>
<td>2 700</td>
<td>69</td>
<td>49</td>
</tr><tr>
<td>7075</td>
<td>455</td>
<td>2 810</td>
<td>71.7</td>
<td>21</td>
</tr><tr>
<td>2A12</td>
<td>310</td>
<td>2 780</td>
<td>73</td>
<td>15</td>
</tr><tr>
<td>Cf/Al</td>
<td>600</td>
<td>2 500</td>
<td>220</td>
<td>600</td>
</tr><tr>
<td>SiCw/Al</td>
<td>500</td>
<td>2 750</td>
<td>200</td>
<td>500</td>
</tr></table>

各变量的取值范围如表4所示。

**表4 设计变量取值范围**

<table border="1" ><tr>
<td>变量</td>
<td>最小值</td>
<td>最大值</td>
</tr><tr>
<td>$X_{1}/\mathrm {mm}$</td>
<td>0.5</td>
<td>3</td>
</tr><tr>
<td>$X_{2}/\mathrm {mm}$</td>
<td>0.5</td>
<td>4.5</td>
</tr><tr>
<td>$X_{3}/\mathrm {mm}$</td>
<td>0.5</td>
<td>6</td>
</tr><tr>
<td>$X_{4}/\mathrm {mm}$</td>
<td>0.5</td>
<td>4.5</td>
</tr><tr>
<td>$X_{5}/\mathrm {mm}$</td>
<td>0.5</td>
<td>4.5</td>
</tr><tr>
<td>$X_{6}/\mathrm {mm}$</td>
<td>45</td>
<td>87</td>
</tr><tr>
<td>$X_{7}$</td>
<td>1</td>
<td>}5</td>
</tr></table>

将前骨架的质量 $M_{\mathrm {G}}$ 、成本 $C_{0}$ 以及工况2下阵列天线增益 $G_{\mathrm {t}}$ 作为优化设计目标。

min: $\left\{\begin{array}{l}M_{\mathrm {G}}(\boldsymbol {X})=\boldsymbol {ρ}\cdot \sum _{k}A_{k}(\boldsymbol {X})h_{k}(\boldsymbol {X})\\ C_{\mathrm {o}}(\boldsymbol {X})=P_{\mathrm {ri}}\cdot \boldsymbol {ρ}\cdot \sum _{k}A_{k}(\boldsymbol {X})h_{k}(\boldsymbol {X})\\ -G_{\mathrm {t}}(\boldsymbol {X})\end{array}\right.$ (28)

式中， $A_{k}$ 和 $h_{k}$ 分别为各零件表面积和厚度。天线增益 $G_{\mathrm {t}}$ 通过天线方向图获取，远场方向图按（29）式进行计算［20］。

$$E(ξ,ζ)=f(ξ,ζ)\sum _{n}I_{n}\exp \left(\mathrm {j}ψ_{n}\right)\exp \left(\mathrm {j}k_{v}\boldsymbol {r}_{n}·\boldsymbol {r}_{0}\right)$$

(29)

式中： $E(ξ,ζ)$ 为在俯仰角 $ξ$ 和方位角 $ζ$ 处的远区场强 $;f(ξ,ζ)$ 为阵元的阵中方向图； $I_{n}$  和 $ψ_{n}$ 分别为第n 个阵元的激励电流幅值和相位； $;r_{0}$ 和 $\boldsymbol {r}_{n}$ 分别为第0号阵元和第n号阵元的位置矢量； $k_{v}$ 为波常数。

约束条件定义为 $[21]$ 

$$\left\{\begin{aligned}&g_{{}_{1}}(\;\boldsymbol {x}\;)=d_{{}_{1\max }}\;-\;\left[\;d_{{}_{1}}\;\right]\;\leq \;0\\ &g_{{}_{2}}(\;\boldsymbol {x}\;)=\sigma _{{}_{2\max }}\;-\;\left[\;\sigma \;\right]\;\leq \;0\\ &\boldsymbol {X}_{{}_{\min }}\;\leq \;\boldsymbol {X}\;\leq \;\boldsymbol {X}_{{}_{\max }}\end{aligned}\right.\tag{30}$$

式中， $d_{1\max }$ 和 $\left[d_{1}\right]$ 分别为工况1下最大变形和允许变形，本文 $\left.d_{1}\right]=1.5$ $\mathrm {mm};σ_{2\max }$  和 $[σ]$ 为最大等效应力和材料许可应力，本 $文[\sigma ]=\sigma /2。$ 

### 4.3 试验设计

在Ansys中进行静力学仿真分析，获取前骨架最大变形、最大等效应力和每个阵元位置矢量。本文通过建立有限元参数化模型，利用 Ansys与Matlab联立的方 $式[22]$ ，为结构优化迭代提供响应值数据。

前骨架有限元模型如图4所示，底板固定部位 $\left(A_{1}\;A_{4}\right)$ 采取全固定约束。在网格划分时选取壳体单元SHELL181模拟各薄壁件，内部组件和电源等

<!-- $A_{1}$ $A_{2}$ $A_{4}$ MASS21 $A_{3}$ A -->
![](https://web-api.textin.com/ocr_image/external/3da93be775bbf05d.jpg)

图4 前骨架有限元模型

<!-- 万方数据 -->

<!-- ·346· -->

<!-- 西北工业大学学报 -->

<!-- 第44卷 -->

设备用质量单元MASS21模拟。风载荷施加在反射板壳单元节点上。

通过Ansys静力学分析，获得 $8\times 8$ 阵列每个天线单元的位置矢量 $\boldsymbol {r}_{n}$ ，代入（30）式，获取阵列天线方向图和增益。图5为理想阵列天线方向图。

<!-- 30 20 aP/ 10 0 -60 -20 20 60 观测角度 $/(^{\circ })$ -->
![](https://web-api.textin.com/ocr_image/external/5826100db48f4c3a.jpg)

图5 理想阵列方向图

采用MOAK算法进行前骨架结构优化，具体实施步骤和参数设置如下：

1）采用拉丁超立方的方法进行初始试验样本采样，初始试验样本 $N_{\mathrm {um}}=300$ 

2）通过Ansys与Matlab联立的方式获得所需响应值，建立阵元位移和约束条件的初始代理模型。

3）利用可行域探索准则添加样本点，其中q取值为1，生成初始近似PF所需 $N_{\mathrm {PS}}$ 为15；

4）利用TCF-SMOTE准则添加样本点， $g_{\mathrm {cl}}=$ 0.2mm, $g_{\mathrm {c}2}=10\mathrm {MPa}$ ，判断参数 $N_{\mathrm {PG}}=800$ ,SMOTE 添加样本数量 $N_{\text {new}}=200$ 

5）更新试验数据，获得近似PF。

### 4.4 优化结果

本节采用MOAK算法对相控阵天线前骨架进行优化设计，并与经典代理模型算法GCMOA进行对比分析，共开展10组试验，算法加点终止条件均为仿真1000次。表5给出了2种算法计算结果。从表5中可以看出，相对GCMOA,MOAK算法具有更好的优化效果和稳定性，并且能够提供更多的解决方案。

**表5 MOAK算法与GCMOA算法计算结果**

<table border="1" ><tr>
<td rowspan="2">优化算法</td>
<td colspan="3">性能指标均值（标准差）</td>
</tr><tr>
<td>PS 数量</td>
<td>GD</td>
<td>RHV</td>
</tr><tr>
<td>GCMOA</td>
<td>28<br>(5.8284)</td>
<td>5.142 7<br>(3.093 2)</td>
<td>0.0810<br>(0.039 4)</td>
</tr><tr>
<td>MOAK</td>
<td>96<br>(4.535 5)</td>
<td>2.318 7<br>(1.008 4)</td>
<td>0.066 8<br>(0.033 7)</td>
</tr></table>

以常规优化算法NSGA-III（初始种群为200，迭代次数为100）为参考基准，图6给出了MOAK算法、GCMOA算法和NSGA-III算法的PF轮廓对比结果。从图6可以看出，GCMOA算法能够描绘出PF 的大致轮廓，但由于PS集数量较少，对PF的刻画不够完整；而MOAK算法所描绘的近似PF与NSGA-III算法的PF完美吻合，甚至提供了一些NSGA-III算法未曾寻到的解决方案。

<!-- 25.612 SP/ 25.611 25.610 8 4 120 成本 $/10^{4}$ 元 80 0 40 质量 $/kg$ -->
![](https://web-api.textin.com/ocr_image/external/a26da36513a1fd8b.jpg)

a)GCMOA算法

<!-- aP/ 25.611 5 $25.610$ $5$ 25.609 58 4 120 成本 $/10^{4}$ 元 80 0 40 质量 $/kg$ -->
![](https://web-api.textin.com/ocr_image/external/9960f7625a1f2081.jpg)

b) MOAK算法

<!-- MOAK 25.612 NSGA-III aP/ 25.610 25.608 25.606 8 4 120 成本 $/10^{4}$ 元 80 0 40 质量 $/kg$ -->
![](https://web-api.textin.com/ocr_image/external/06847eadf3fa3978.jpg)

c) NSGA-III算法

图6 MOAK算法、GCMOA算法和NSGA-III算法得到的近似PF轮廓

表6给出了MOAK算法、GCMOA算法及参考算法NSGA-III的单次计算时长对比结果。其中计算时长由仿真时长和算法迭代时长组成。从表6中可以看出，NSGA-III运行20 000次（达到迭代次数）有限元仿真，计算时长达86.66h；而MOAK与GCMOA 算法执行了1000次（算法终止条件）有限元仿真，计算时长分别为14.91h和13.08h，较NSGA-III分别减少了82.79％和84.91％。由于加点准则不一样，MOAK算法的计算时长略低于GCMOA算法，优化效率更高。可见，随着工程结构复杂性增加（单次仿真耗时更长）,MOAK算法计算效率优势将更加凸显。

<!-- 万方数据 -->

<!-- 第2期 -->

<!-- 于驰，等：MOAK：一种面向相控阵天线机电耦合设计的多目标代理模型优化算法 -->

<!-- ·347· -->

**表6 MOAK算法与GCMOA算法计算时长分析**

<table border="1" ><tr>
<td>算法</td>
<td>计算时长／h</td>
<td> 算法迭代时长／h</td>
<td>仿真次数</td>
<td>单次仿真耗时／s</td>
<td> 仿真时长／h</td>
<td>时长缩减／％</td>
</tr><tr>
<td>NSGA-III（参考）</td>
<td>86.66</td>
<td>3.33</td>
<td>20 000</td>
<td>15</td>
<td>83.33</td>
<td></td>
</tr><tr>
<td>GCMOA</td>
<td>14.91</td>
<td>10.74</td>
<td>1000</td>
<td>15</td>
<td>4.17</td>
<td>82.79</td>
</tr><tr>
<td>MOAK</td>
<td>13.08</td>
<td>8.91</td>
<td>1000</td>
<td>15</td>
<td>4.17</td>
<td>84.91</td>
</tr></table>

表7展示了从MOAK算法生成的PS解集中选取的4个候选优化方案。其中，优化方案1在2个优化目标上显著提升，总质量减轻了35.4%，成本降低了80.2%，而增益几乎无变化。而优化方案2和优化方案4则牺牲了成本目标，实现了质量显著降低和增益略微提升。优化方案3实现了总质量减轻45.8%，成本降低83.4%，增益略微降低。这些方案为实际工程提供了多样性解决策略，以满足不同设计需求。

**表7 前骨架多目标优化设计候选方案**

<table border="1" ><tr>
<td>优化方案</td>
<td>$X_{1}/\mathrm {mm}$</td>
<td>$X_{2}/\mathrm {mm}$</td>
<td>$X_{3}/\mathrm {mm}$</td>
<td>$X_{4}/\mathrm {mm}$</td>
<td>$X_{5}/\mathrm {mm}$</td>
<td>$X_{6}/\mathrm {mm}$</td>
<td>$\mathrm {X}_{7}$</td>
<td>$M_{\mathrm {G}}/\mathrm {kg}$</td>
<td>$C_{0}/$元</td>
<td>$G_{\mathrm {t}}/\mathrm {dB}$</td>
</tr><tr>
<td>初始</td>
<td>3.0</td>
<td>4.5</td>
<td>6.0</td>
<td>4.50</td>
<td>4.50</td>
<td>45.00</td>
<td>5A05</td>
<td>125.60</td>
<td>6 153.8</td>
<td>25.610 3</td>
</tr><tr>
<td>1</td>
<td>3.0</td>
<td>0.5</td>
<td>0.5</td>
<td>3.12</td>
<td>3.76</td>
<td>85.58</td>
<td>2A12</td>
<td>81.10</td>
<td>1 216.6</td>
<td>25.6104</td>
</tr><tr>
<td>2</td>
<td>2.63</td>
<td>1.84</td>
<td>0.53</td>
<td>0.50</td>
<td>0.50</td>
<td>52.47</td>
<td>Cf/Al</td>
<td>43.56</td>
<td>26 138.9</td>
<td>25.611 2</td>
</tr><tr>
<td>3</td>
<td>3.0</td>
<td>0.5</td>
<td>0.5</td>
<td>1.28</td>
<td>2.79</td>
<td>82.30</td>
<td>2A12</td>
<td>68.05</td>
<td>1 020.7</td>
<td>25.609 9</td>
</tr><tr>
<td>4</td>
<td>3.0</td>
<td>4.50</td>
<td>6.0</td>
<td>4.50</td>
<td>4.50</td>
<td>45.00</td>
<td>Cf/Al</td>
<td>116.28</td>
<td>69 771.0</td>
<td>25.611 8</td>
</tr></table>

对优化方案1进行了Ansys仿真分析，图7展示了工况1下的位移云图。从图7可以看出，最大位移为1.26mm，发生在反射板的中心区域，小于许可位移1.5mm。图8展示了工况2下的应力云图，其中最大等效应力达到126.9 MPa，小于许可应力155 MPa。因此，优化后的前骨架满足刚度和强度约束要求。

<!-- NODAL SOLUTION STEP-1 ANSYS SUB-1 NOV 14 2024 22 10:38:14 USUM (AVG) RSYS-0 DHX-1.2656 SHX =1.2656 DMX 0 0.281 244 0.562 487 0.843 731 1.12497 0.140 622 0.421 866 0.703 109 0.984 353 1.2656 -->
![](https://web-api.textin.com/ocr_image/external/88496b825f63c9bb.jpg)

图7 工况1下位移云图

<!-- NODAL SOLUTION ANSYS ST EP-1 2020R2 SUB-1 NOV 14 2024 TIME-1 10:33:1 SBQV (AVO) DMX =8.556 05 SMX-126.904 SMX 0 28.2186 56.4373 84.6559 112.875 14.1093 42.328 70.5466 98.7652 126.984 -->
![](https://web-api.textin.com/ocr_image/external/96831db7ca0393b8.jpg)

图8 工况2下应力云图

## 5 结论

针对复杂多目标约束优化问题，本文提出了一种基于Kriging模型的多目标代理优化算法-MOAK算法。该算法通过创新设计的组合加点准则实现高精度Pareto最优解集求解。组合加点准则主要包括两部分：可行域探索准则和TCF-SMOTE准则。首先利用可行域探索准则实现所有可行域的有效辨识，得到初始近似PF轮廓。然后利用提出的TCF-SMOTE准则，改进初始近似PF轮廓，直至形成完整的Pareto前沿。在3个标准测试函数的对比试验中，MOAK算法展现出了显著优势，改善了常规代理模型优化算法中PS稀疏、PF刻画不完整的问题。

<!-- 万方数据 -->

<!-- ·348· 西北工业大学学报 第44卷 -->

<!-- 万方数据 -->

最后，针对相控阵天线前骨架优化设计问题，构建了包含结构质量、成本、辐射性能指标（增益）的多目标优化模型，利用MOAK算法进行求解。实例结果表明，MOAK算法在结构轻量化、成本和辐射性能平衡方面提供了多样性解决方案，满足不同设计偏好需求。此外，在与NSGA-III算法取得相当的优化精度前提下，MOAK算法减少了仿真次数，提升了复杂工程问题优化效率。本文研究为相控阵天线机电耦合优化设计提供了兼具精度与效率的有效方法。

虽然MOAK算法在提升多目标约束优化问题Pareto前沿完整性方面表现出显著优势，但是本研究仍存在以下局限：首先是“维度灾”问题，算法计算复杂度随维度增长呈非线性上升，导致代理模型对约束条件和目标函数的拟合精度下降且计算成本剧增，制约了高维工程问题的应用；其次，对强离散组合优化问题的适应性不足。未来考虑引入变分自编码器压缩设计空间，降低高维问题建模复杂度；研究离散-连续混合决策范式，提升算法在离散主导场景的优化效率与精度。

## 参考文献：

［1］张帅，赖国泉，徐文莉，等．有源相控阵天线阵面冷板设计与优化［J]．低温与超导，2023,51(10):61-67．ZHANG Shuai,LAI Guoquan, XU Wenli, et al. Design and optimization of cold plate of active phased array antenna[J]. Cryoge-nics & Superconductivity, 2023,51(10):61-67.(in Chinese)

［2］朱志远，关宏山．相控阵雷达天线阵面基本模块结构设计［J]．雷达与对抗，2021,41(4):44-46．ZHU Zhiyuan, GUAN Hongshan. Basic module structure design of basic modules of antenna array for phased array radars[J]. Radar & Electronic Countermeasures,2021,41(4):44-46.(in Chinese)

［3］张磊，许帅康，陈洁，等．列车车体轻量化设计研究进展［J]．机械工程学报，2023,59(24):177-196．

ZHANG Lei,XU Shuaikang,CHEN Jie, et al. Research progress in lightweight design of train body[J].Journal of Mechanical Engineering,2023,59(24):177-196.(in Chinese)

[4] KEANE F A J. Recent advances in surrogate-based optimization[J]. Progress in Aerospace Sciences, 2009,45(1/2/3): 50-79.

[5] WANG G G, SHAN S. Review of metamodeling techniques in support of engineering design optimization[J]. Journal of Mechan-ical Design,2007,129(4):370-380.

［6］白桦，叶茂，杨光，等．基于Kriging代理模型的新型箱梁气动外形优化［J]．振动与冲击，2025,44(10):58-65．BAI Hua, YE Mao, YANG Guang, et al. Aerodynamic shape optimization of a new box girder based on the Kriging surrogate model[J]. Journal of Vibration and Shock, 2025,44(10):58-65.(in Chinese)

［7］欧阳林寒，黄磊，韩梅．主动学习可靠性分析算法：基于Kriging预测方差的视角［J]．系统工程理论与实践，2023,43(7):2154-2165.

OUYANG Linhan, HUANG Lei, HAN Mei. Active learning reliability analysis algorithm: a perspective based on Krigingpredic-tion variance[J]. System1s Engineering Theory and Practice, 2023, 43(7):2154-2165.(in Chinese).

[8] GONG Y, ZHANG J, XU D, et al. Quantile-based optimization under uncertainties for complex engineering structures using an active learning basis-adaptive PC-Kriging model[J]. Chinese Journal of Aeronautics, 2025,38(1):103197.

［9］张文栋，石明辉，秦东晨，等．基于Kriging模型的锥形气体静压轴承优化设计［J]．轴承，2025(1):14-22．ZHANG Wendong, SHI Minghui, QIN Dongchen, et al. Optimization design of conical hydrostatic gas bearings based on Kriging model[J].Bearings,2025(1):14-22.(in Chinese)

［10］王金涛，徐平，铁瑛，等．约束并行自适应代理模型优化算法及在弧形闸门优化设计中的应用［J]．计算机集成制造系统，2024,30(10):3502-3513．

WANG Jintao,XU Ping, TIE Ying, et al. Constrained parallel adaptive surrogate model optimization algorithm and its applica-tion in optimization design of radial gates[J]. Compuiter Integrated Manufacturing Systems, 2024,30(10):3502-3513.(in Chi-

<!-- 第2期 于驰，等：MOAK：一种面向相控阵天线机电耦合设计的多目标代理模型优化算法 -->

<!-- ·349· -->

<!-- 万方数据 -->

nese)

[11] EMMERICH M T M, DEUTZ A H, KLINKENBERG J W. Hypervolume-based expected improvement: monotonicity properties and exact computation[C]//2011 IEEE Congress on Evolutionary Computation, New Orleans, 2011:2147-2154.

[12] MARTÍNEZ-FRUTOS J, HERRERO-PÉREZ D. Kriging-based infill sampling criterion for constraint handling in multi-objective optimization[J]. Journal of Global Optimization,2016,64(1):97-115.

［13］张建侠，宋明顺，方兴华，等．基于Kriging模型的多目标代理优化算法及其收敛性评估［J]．计算机集成制造系统，2021,27(7):2035-2044.

ZHANG Jianxia, SONG Mingshun, FANG Xinghua, et al. Kriging-assisted multi-objective optimization algorithm and its conver-gence assessment[J]. Computer Integrated Manufacturing Systems,2021,27(7):2035-2044.(in Chinese)

[14] LIU L, LI Z, KANG H, et al. Review of surrogate model assisted multi-objective design optimization of electrical machines: new opportunities and challenges[J]. Renewable and Sustainable Energy Reviews,2025,215:115609.

[15] LEE U. A new adaptive Kriging-based optimization(AKBO) framework for constrained optimization problems: a case study on shared autonomous electric vehicle system design[J]. Expert Systems with Applications, 2024,252:124147.

［16］龙周，陈松坤，王德禹．基于SMOTE算法的船舶结构可靠性优化设计［J]．上海交通大学学报，2019,53(1):26-34．LONG Zhou, CHEN Songkun, WANG Deyu. Reliability-based design optimization of ship structures based on SMOTE algorithm [J]. Journal of Shanghai Jiao Tong University, 2019,53(1):26-34.(in Chinese)

[17] YEN G G, HE Z. Performance metric ensemble for multiobjective evolutionary algorithms[J]. IEEE Trans on Evolutionary Com-putation, 2014,18(1):131-144.

［18］龙日升，赵超，张义民，等．基于APDL的叶脉参数化建模与有限元分析［J]．机械设计与制造，2023(10):223-226．LONG Risheng, ZHAO Chao, ZHANG Yimin, et al. Parametric modeling and finite element analysis of leaf veins based on AP-DL[J]. Machinery Design & Manufacture, 2023(10):223-226.(in Chinese)

［19］刘英林，赵军．一种计算风载荷的施加方法［C]/／北京力学会第14届学术年会，北京，2008:411-412．LIU Yinglin, ZHAO Jun. A method for applying wind load[C]//Proceedings of the 14th Academic Annual Conference of Beijing Society of Theoretical and Applied Mechanics,Beijing,2008:411-412.(in Chinese)

［20］胡祥涛，谢安祥．相控阵天线碗状变形对其辐射性能的影响分析［J]．电子机械工程，2024,40(2):59-64．HU Xiangtao, XIE Anxiang. Analysis of effect of bowl-shaped deformation on radiation performance of phased array antenna[J]. Electro-Mechanical Engineering,2024,40(2):59-64.(in Chinese)

［21］周小龙，操卫忠．模块化在某雷达阵面结构设计中的应用［J]．电子机械工程，2021,37(1):1-3．ZHOU Xiaolong, CAO Weizhong. Application1 of modular design to structure design of a radar antenna array[J]. Electro-Mecha-nical Engineering,2021,37(1):1-3.(in Chinese)

［22］邱爽，周金宇．基于MATLAB和ANSYS的协同优化设计及应用［J]．机械设计，2018,35(9):68-72．QIU Shuang, ZHOU Jinyu. Collaborative optimization design and application based on MATLAB and ANSYS[J]. Journal of Machine Design,2018,35(9):68-72.(in Chinese)

<!-- ·350· -->

<!-- 西北工业大学学报 -->

<!-- 第44卷 -->

### MOAK: a multi-objective surrogate model optimization algorithm forelectromechanical coupling design of phased array antennas

YU Chi,HU Xiangtao

(School of Electrical Engineering and Automation, Anhui University, Hefei 230000,China)

**Abstract:** To reduce the mass and manufacturing cost of phased array antennas while ensuring stable radiation per-formance under harsh operating conditions, this paper proposes a multi-objective surrogate optimization algorithm based on the Kriging model-MOAK. The algorithm integrates a feasible-region exploration criterion with a TCF-SMOTE criterion to form a hybrid infill strategy. The feasible-region exploration criterion enables effective identifica-tion of the global feasible space, after which the innovative TCF-SMOTE criterion is employed to construct a com-plete Pareto front, thereby achieving high-accuracy Pareto-optimal solution sets. Specifically,the TCF-SMOTE cri-terion suppresses extreme constraint violations in Kriging model construction through a truncated constraint function (TCF), mitigating model distortion near feasible-domain boundaries. In addition, the SMOTE algorithm is applied to generate synthetic sample points, increasing the number of Pareto solutions (PS) and improving the completeness of the Pareto front (PF). Comparative experiments on three standard benchmark functions demonstrate that the MOAK algorithm significantly outperforms conventional surrogate-model-based optimization al-gorithms, alleviating the problems of sparse PS distribution and incomplete PF characterization. When applied to the optimization design of the pre-skeleton of a phased array antenna, MOAK provides diverse solutions for balan-cing structural lightweighting, cost, and radiation performance, thereby meeting different design preference require-ments. In a representative optimized design, MOAK achieved a 35.4% reduction in mass, an 80.2% reduction in cost,and an 84.91% reduction in computation time, while maintaining stable radiation performance.

**Keywords:** Kriging; surrogate model; infill criterion; multi-objective optimization; phased array antenna

引用格式：于驰，胡祥涛.MOAK：一种面向相控阵天线机电耦合设计的多目标代理模型优化算法［J].西北工业大学学报，2026,44(2):339-350.

YU Chi, HU Xiangtao. MOAK; a multi-objective surrogate model optimization algorithm for electromechanical coupling de-sign of phased array antennas[J]. Journal of Northwestern Polytechnical University,2026,44(2):339-350.(in Chinese)

© 2026 Journal of Northwestern Polytechnical University.

This is an Open Access article distributed under the terms of the Creative Commons Attribution License (http://creativecommons.org/licenses/by/4.0),which permits unrestricted use, distribution, and reproductionin any medium, provided the original work is properly cited.

<!-- 万方数据 -->

