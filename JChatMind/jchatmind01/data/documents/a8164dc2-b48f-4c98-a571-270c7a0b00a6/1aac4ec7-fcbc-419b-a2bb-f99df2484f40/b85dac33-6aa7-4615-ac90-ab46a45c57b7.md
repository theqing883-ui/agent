<!-- 第54卷 第3期 -->

<!-- 华中科技大学学报（自然科学版） -->

<!-- Vol.54 No.3 -->

<!-- 2026年3月 -->

<!-- J.Huazhong Univ. of Sci. & Tech.(Natural Science Edition) -->

<!-- Mar. 2026 -->

<!-- 万方数据 -->

**DOI**:10.13245/j.hust.250742

# 基于聚类改进MUSIC的多目标方向感知算法

韩曦¹万继银¹李沛阳¹毕福昆¹司黎明²

（1．北方工业大学人工智能与计算机学院，北京100144；

2．北京理工大学集成电路与电子学院，北京100081）

**摘要** 针对智能反射面辅助的感知系统中，基于能量占比的子空间分类准则在低信噪比下极易出现方向角（DOA）多估的问题，提出了一种基于密度聚类改进多信号分类（DENCLUE-MUSIC）的多目标方向感知算法.首先对接收信号自相关矩阵进行特征值分解；然后使用DENCLUE算法估计目标个数，得到噪声子空间；最后使用谱峰搜索获取多个目标的方向角，仿真结果表明：与现有的基于特征值聚类的MUSIC算法相比，本文算法的复杂度更低，计算时间更短，体现了所提DENCLUE-MUSIC算法的优势.

**关键词** 目标感知；角度估计；MUSIC算法；聚类；谱峰搜索

**中图分类号** TN928

**文献标志码 A**

**文章编号** 1671-4512(2026)03-0085-07

# Multi-target directional sensing algorithm based on clustering improved MUSIC

**HAN** Xi WAN Jiyvin' LI Peiyang' BI Fukun' SI Liming

(1. School of Artificial Intelligence and Computer Science, North China University of Technology, Beijing 100144,China;

2. School of Integrated Circuit and Electronics, Beijing Institute of Technology, Beijing 100081,China)

**Abstract** In the intelligent reflective surface-assisted sensing system, the subspace classification criterion based on the energy occupation ratio is highly prone to direction of angle (DOA) overestimation at low signal-to-noise ratios, to address the above problems,a multi-objective direction sensing algorithm based on the density based clustering improves multiple signal classification (DENCLUE-MUSIC) multi-target direction sensing algorithm was proposed. Thne proposed algorithm first performs an eigenvalue decomposition of the autocorrelation matrix of the received signal, then uses the DENCLUE algorithm to estimate the number of targets to obtain the noise subspace, and finally uses the spectral peak search to obtain the DOA of multiple targets. Simulation results show that compared with the traditional MUSIC algorithm based on eigenvalue clustering, the algorithm in this paper has lower complexity and shorter computation time,reflecting the advantages of the proposed DENCLUE-MUSIC algorithm.

**Key words** target sensing; angle estimation; MUSIC algorithm; clustering; spectral peak search

第六代（6th Generation,6G）无线通信系统出现了许多基于位置／方向感知的应用场景，如机器人导航、无人驾驶等2]．这些应用对通信系统的通感性能提出了更高要求，如超高精度、超低延迟和无缝覆盖等3].2022年11月国际电信联盟通信部门5D工作组正式发布《2030年及以后地面国际移动通信系统的未来技术趋势》4]，报告中介绍了一种新兴技术-智能反射面（intelligent reflecting surface,IRS)[5-6],IRS能实时动态调整反射信号的相位，重构无线传播信道，达到绕过障碍、重塑信道分布和增加信道秩的目的7-8]．近年来基于IRS辅助的感知算法也逐渐成为新的研究热点9．已有一些学者对无源IRS辅助的感知算法进行了研究［10]，通过部署IRS，确保基站与目标间视距链路的建

**收稿日期** 2024-11-09．

**作者简介** 韩曦（1983-)，女，副教授，E-mail:jinlucky333@126.com．

**基金项目** 国家自然科学基金资助项目（62001008)；北方工业大学毓秀创新项目（2024NCUTYXCX201,2024NCUTYX-CX119).

<!-- ·86· -->

<!-- 华中科技大学学报（自然科学版） -->

<!-- 第54卷 -->

立，能提高感知系统的精度，解决了传统单／双静态感知系统的缺陷［11-12]．为进一步提高感知精度，文献［13］提出了半无源IRS辅助的感知系统模型，与无源IRS辅助的感知系统相比，半无源IRS感知系统减少了一条反射波束，共含有一条直射波束和两条级联反射波束，降低了信道级联衰弱效应［14]．

为进一步提高感知系统性能，文献［15］建立了有源IRS辅助的感知系统，该系统中IRS感知单元接收的信号波束比半无源IRS辅助的感知系统更多，控制器与反射单元的距离远小于基站与反射单元的距离，因此有源IRS感知系统的感知精度相对较高.

文献［15］提出的目标方向感知算法只能估计单目标的到达方向（direction of arrival,DOA)，无法用于多目标感知场景.为解决该问题，本研究提出了一种基于密度聚类改进多信号分类（density based clustering improves multiple signal classification, DENCLUE-MIUSIC）的多目标方向感知算法，该算法利用信号特征值与噪声特征值的本质差异，采用DENCLUE 2.0技术对特征值进行聚类，获取噪声子空间，最后通过谱峰搜索获取目标的方向信息，有效克服了传统MUSIC算法基于能量占比准则估计准确度差的缺陷.

## 1 目标感知系统模型

### 1.1 信号传播分析

有源IRS可以分为IRS感知单元、IRS反射单元和IRS控制器三部分.IRS控制器作为信号源向环境中发射探测信号.反射单元共有 $N_{0}$ 个， $N_{0}=$ $N_{\mathrm {h}}\times N_{\mathrm {v}},$ $N_{\mathrm {h}}$ 和 $N_{v}$ 分别表示水平方向和垂直方向的IRS单元数目，反射单元间距为 $d_{r},$ ，其只能反射信号.紧挨着反射单元的下方，横向排列着数目为 $M_{h}$ 的感知单元，右方纵向排列着数目为1 $M_{v}$ 的感知单元.感知单元用于接收到达单元上的无线信号，其间距为a $d_{s}$ 

感知目标的DOA同时包含方位角与仰角，可分别由横向与纵向感知单元获取.这里将以水平单元估计方位角为例详述算法原理，该方法同样适用于仰角估计.

有源IRS感知系统如图1所示，该系统由感知目标、IRS控制器、IRS单元组成，假设感知的目标共有I个，以第i个为例.信号传播过程可概括为，控制器持续发射探测信号 $x(t)$ )，经过IRS反射单元和目标反射后，最终被IRS感知单元接收.记均匀线性阵列（uniform linear array,ULA）导向向量函数为

<!-- IRS控制器 $\boldsymbol {h}_{i}^{\mathrm {TC}}$ $\boldsymbol {h}^{\mathrm {RC}}$ $h^{\mathrm {SC}}$ $\boldsymbol {h}_{i}^{\mathrm {TR}}$ $h_{i}^{ST}$ 第i个感知目标 ■IRS反射单元； IRS感知单元. -->
![](https://web-api.textin.com/ocr_image/external/9a9747dc7944c2d1.jpg)

图1 有源IRS辅助的感知系统图

$$f(Φ,d,M)=$$

$$\left[1,\quad \mathrm {e}^{-\frac {\mathrm {j}2\pi d\Phi }{A}},\quad \cdots ,\quad \mathrm {e}^{-\frac {\mathrm {j}2\pi d(M-1)\Phi }{A}}\right]^{\mathrm {T}}\in \mathbf {C}^{M\times 1},\tag{1}$$

式中： $Φ$ 为信号到达IRS阵列角度的正弦值；d为ULA的阵元间距；M为阵元个数；A为载波在自由空间中的波长.IRS感知单元的接收信号由三部分组成，接下来依次分析.

第一部分信号的传输路径为由IRS控制器传输至IRS感知单元，IRS感知单元的接收信号可以写为

$$y^{SC}=h^{SC}x(t),\tag{2}$$

式中 $\boldsymbol {h}^{\mathrm {SC}}\in \mathbf {C}^{M_{h}\times 1}$ 表示IRS控制器与IRS感知单元之间通信链路的信道向量，即

$$h^{SC}=G^{SC}f(\sin \alpha ^{SC},d_{s},M_{h}),\tag{3}$$

其中， $G^{\mathrm {SC}}\in \mathbf {C}$ 为控制器与感知单元之间信道的复增益， $d_{s}$ 为IRS感知单元间距， $\alpha ^{SC}\in [-90^{\circ },$ ， $90^{\circ }$ °］为控制器到感知单元的方位角，且属于系统先验信息，假设已知.由于 $y^{SC}$ 中只含有环境信息，不包含与目标相关的角度信息，因此称 $h^{SC}$ 为背景环境信道，称 $y^{SC}$ $)$ 为背景环境信号.

第二部分信号的传播路径为：由控制器传输至IRS反射单元，再传输至感知目标，最后传输至IRS感知单元，IRS感知单元接收的信号表达式为

$$y_{i}^{\mathrm {STRC}}=\boldsymbol {h}_{i}^{\mathrm {ST}}\boldsymbol {h}_{i}^{\mathrm {TR}}\text {diag}(\boldsymbol {w}(t))\boldsymbol {h}^{\mathrm {SC}}x(t)=\boldsymbol {H}_{i}^{\mathrm {STRC}}x(t);(4)$$

$\boldsymbol {H}_{i}^{\mathrm {STRC}}=\boldsymbol {h}_{i}^{\mathrm {ST}}\boldsymbol {h}_{i}^{\mathrm {TR}}$ $\text {diag}(\boldsymbol {w}(t))\boldsymbol {h}^{\mathrm {SC}}\in \mathbf {C}^{M_{k}\times N},$  (5)

式中 $w(t)\in \mathbf {C}^{N\times 1}$ 为IRS反射单元的反射相位向量； $\boldsymbol {h}^{\mathrm {RC}}\in \mathbf {C}^{N\times 1},$ $\boldsymbol {h}_{i}^{\mathrm {TR}}\in \mathbf {C}^{1\times N}$ 和 $\boldsymbol {h}_{i}^{\mathrm {ST}}\in \mathbf {C}^{M_{h}\times 1}$ 分别表示控制器与反射单元之间的信道向量、反射单元与第i个感知目标之间的信道向量及第i个感知目标与感知单元之间的信道向量，这些信道的表达式为

$$h_{i}^{ST}=G_{i}^{ST}f(\sin \alpha _{i}^{ST},d_{s},M_{h}),\tag{6}$$

$\boldsymbol {h}_{i}^{\mathrm {TR}}=G_{i}^{\mathrm {TR}}(\boldsymbol {f}(\sin \alpha _{i}^{\mathrm {TR}}\sin \beta _{i}^{\mathrm {TR}},d_{\mathrm {r}},$ $,$ $N_{\mathrm {h}})$ ⊗

$$f(\cos \beta _{i}^{TR},d_{r},N_{v})^{T},\tag{7}$$

$\boldsymbol {h}^{RC}=G^{RC}\boldsymbol {f}(\sin \alpha ^{RC}\sin \beta ^{RC},$  $d_{\mathrm {r}}$  $\left.N_{\mathrm {h}}\right)$ ⊗

$$f(\cos \beta ^{RC},d_{r},N_{v}),\tag{8}$$

<!-- 万方数据 -->

<!-- 第3期 -->

<!-- 韩曦，等：基于聚类改进MUSIC的多目标方向感知算法 -->

<!-- ·87· -->

<!-- 万方数据 -->

式中： $\alpha _{i}^{ST}\in [-90^{\circ },90^{\circ }]$ 和 $G_{i}^{\mathrm {ST}}\in \mathbf {C}$ 分别为从第i个感知目标到达感知单元的方位角和该信道的路径增益； $\alpha _{i}^{\mathrm {TR}}\in \left[-90^{\circ },90^{\circ }\right]$ 和 $\beta _{i}^{\mathrm {TR}}\in \left[-90^{\circ },90^{\circ }\right]$ 分别为从反射单元到达第i个感知目标的方位角和仰角； $G_{i}^{\mathrm {TR}}$ 为反射单元与第i个感知目标之间信道的复增益； $\alpha ^{RC}\in [-90^{\circ }$ $,90^{\circ }]$ 和 $G^{\mathrm {RC}}$ 为控制器到反射单元的方位角和该信道的路径增益，与角度 $\alpha ^{SC}$ 类似， $\alpha ^{RC}$ 也属于系统先验信息.

第三部分信号的传播路径为：信号由IRS控制器传输至感知目标，再到IRS感知单元.此时，IRS感知单元接收的信号为

$$y_{i}^{STC}=h_{i}^{STC}x(t),\tag{9}$$

式中： $\boldsymbol {h}_{i}^{\mathrm {STC}}=\boldsymbol {h}_{i}^{\mathrm {ST}}\boldsymbol {h}_{i}^{\mathrm {TC}}\in \mathbf {C}^{M_{k}\times 1};$ $h_{i}^{\mathrm {TC}}=G_{i}^{\mathrm {TC}}\in \mathbf {C}$ 为控制器与第i个感知目标之间的信道； $G_{i}^{\mathrm {TC}}\in \mathbf {C}$ 为该信道的复增益.综上所述，最终IRS感知单元接收的第i个感知目标反射信号可以表示为

$$y_{i}^{\mathrm {tot}}=y^{\mathrm {SC}}+y_{i}^{\mathrm {STRC}}+y_{i}^{\mathrm {STC}}+n_{i}^{\mathrm {tot}}(t),\tag{10}$$

式中 $\boldsymbol {n}_{i}^{\mathrm {tot}}(t)\in \mathbf {C}^{M_{k}\times 1}$ 为接收信号中的噪声分量.

### 1.2 信号处理

为从接收信号 $\boldsymbol {y}_{i}^{\mathrm {tot}}$ 中消除通过背景信道到达IRS 感知单元的背景环境信号 $y^{SC}$ 的影响，将算法的估计过程分为背景采集和感知检测两个阶段.当环境中不存在感知目标时，算法处于背景采集阶段，接收信号中只含有背景环境信号 $\boldsymbol {y}^{\mathrm {SC}}$ ；当环境中出现感知目标时，将接收的总信号 $y_{i}^{\mathrm {tot}}$ 与背景信号 $\boldsymbol {y}^{\mathrm {SC}}$ 相减，就可以获得有效信号 $\boldsymbol {y}_{i}^{\mathrm {eff}}$ .同时，接收信号中还应包含高斯噪声分量，即最终获得的有效信号为

$$y_{i}^{\mathrm {eff}}(t)=y_{i}^{\mathrm {tot}}-y^{\mathrm {SC}}=$$

$$h_{i}^{ST}h_{i}^{TR}diag(w(t))h^{RC}x(t)+\tag{11}$$

$$\boldsymbol {h}_{i}^{\mathrm {ST}}h_{i}^{\mathrm {TC}}x(t)+\boldsymbol {n}_{i}^{\mathrm {eff}}(t),$$

式中： $\boldsymbol {n}_{i}^{\mathrm {eff}}(t)=\boldsymbol {n}_{i}^{\mathrm {tot}}(t)-\boldsymbol {n}_{i}^{\mathrm {SC}}(t)\in \mathbf {C}^{M_{k}\times 1}$ 为IRS感知单元接收有效信号中的噪声分量； $n_{i}^{SC}$ 为背景采集阶段IRS感知单元接收到的噪声分量.

为简化IRS时间和空间的无源反射设计，可以使用两个向量的Kronecker积表示反射向量 $\boldsymbol {w}(t)^{[14]}$ ，即

$$\boldsymbol {w}(t)=\boldsymbol {w}_{h}(t)\otimes \boldsymbol {w}_{v}(t),\tag{12}$$

式中 $w_{h}=[e^{jw_{1}},e^{jw_{2}},\cdots ,e^{jw_{N_{k}}}]^{T}$ 和 $w_{v}=[e^{jw_{1}},e^{jw_{2}},$ ..·, $\left.\mathrm {e}^{\mathrm {j}w_{N_{c}}}\right]^{\mathrm {T}}$ 分别为IRS反射单元的水平和垂直反射向量.

假设 $x(t)=1$ ，代入式（11)，可得

$$y_{i}^{\mathrm {eff}}(t)=\boldsymbol {h}_{i}^{\mathrm {ST}}\boldsymbol {h}_{i}^{\mathrm {TR}}\text {diag}(\boldsymbol {w}(t))\boldsymbol {h}^{\mathrm {RC}}x(t)+$$

$$\boldsymbol {h}_{i}^{\mathrm {ST}}\boldsymbol {h}_{i}^{\mathrm {TC}}\boldsymbol {x}(t)+\boldsymbol {n}_{i}^{\mathrm {eff}}(t)=$$

$$\boldsymbol {h}_{i}^{\mathrm {ST}}\left(h_{i}^{\mathrm {TRC}}+h_{i}^{\mathrm {TC}}\right)+\boldsymbol {n}_{i}^{\mathrm {eff}}(t),\tag{13}$$

式中 $h_{i}^{\mathrm {TRC}}=\boldsymbol {h}_{i}^{\mathrm {TR}}\text {diag}(\boldsymbol {w}(t))\boldsymbol {h}^{\mathrm {RC}}\in \mathbf {C}$ 是一个复数域标量.

根据矩阵恒等变换性质，可以进一步得到

$$h_{i}^{\mathrm {TRC}}=G_{i}^{\mathrm {TRC}}\left(\boldsymbol {f}^{\mathrm {T}}\left(\sin \alpha _{i}^{\mathrm {TRC}},d_{\mathrm {r}},N_{\mathrm {h}}\right)\boldsymbol {w}_{\mathrm {h}}(t)\right)\otimes$$

$$\left(f^{T}\left(\cos \beta _{i}^{TRC},d_{t},N_{v}\right)w_{v}(t)\right),\tag{14}$$

式中： $\sin \alpha _{i}^{TRC}=\sin \alpha _{i}^{TR}\sin \beta _{i}^{TR}+\sin \alpha ^{RC}\sin \beta ^{RC}$ ; $\cos \beta _{i}^{TRC}=\cos \beta _{i}^{TR}+\cos \beta ^{RC}.$ 

须要注意的是， $f^{T}(\sin \alpha _{i}^{TRC},d_{r},N_{h})w_{h}(t)$ 和 $\boldsymbol {f}^{\mathrm {T}}\left(\cos \beta _{i}^{\mathrm {TRC}}\right.$  $\left.d_{\mathrm {r}},N_{\mathrm {v}}\right)\boldsymbol {w}_{\mathrm {v}}(t)$ 的运算结果均为复数域标量，因此式（14）中Kronecker运算可写为标量乘积运算，即：

$$\boldsymbol {h}_{i}^{\mathrm {TRC}}=G_{i}^{\mathrm {TRC}}\left(\boldsymbol {f}^{\mathrm {T}}\left(\cos \beta _{i}^{\mathrm {TRC}},d_{\mathrm {r}},N_{\mathrm {v}}\right)\right)\cdot$$

$$f^{T}(\sin \alpha _{i}^{TRC},d_{t},N_{h})w_{h}(t)=$$

$$\bar {G}_{i}^{\mathrm {TRC}}\boldsymbol {f}^{\mathrm {T}}\left(\sin \alpha _{i}^{\mathrm {TRC}},d_{\mathrm {r}},N_{\mathrm {h}}\right)\boldsymbol {w}_{\mathrm {h}}(t),\tag{15}$$

式中 $\bar {G}_{i}^{\mathrm {TRC}}=G_{i}^{\mathrm {TRC}}\boldsymbol {f}^{\mathrm {T}}\left(\cos \beta _{i}^{\mathrm {TRC}},d_{r},N_{v}\right)\boldsymbol {w}_{v}(t)\in \mathbf {C}.$ 

将式（15）代入式（13)，可得：

$$\boldsymbol{y}_{i}^{\mathrm{eff}}(t)=\boldsymbol{f}\left(\sin \alpha_{i}^{\mathrm{ST}},d_{\mathrm{s}},M_{\mathrm{h}}\right)\left(\overline {G}_{i}^{\mathrm {STR}}\boldsymbol {f}^{\mathrm {T}}\left(\sin \alpha_{i}^{\mathrm{TRC}}\right.\right.\quad \left.\left.d_{t},N_{h}\right)w_{h}(t)+G_{i}^{STC}\right)+n_{i}^{eff}(t).\tag{16}$$

在远场条件下，可认为反射单元到目标的DOA等于目标到感知单元的DOA，即 $\alpha _{i}^{\mathrm {ST}}=\alpha _{i}^{\mathrm {TR}}$ 另外假设IRS垂直反射向量已被均衡.根据先验信息可得

$$y_{i}^{\mathrm {eff}}(t)=\boldsymbol {a}(\alpha _{i}^{\mathrm {ST}})c_{t}(\alpha _{i}^{\mathrm {ST}})+\boldsymbol {n}_{i}^{\mathrm {eff}}(t)\tag{17}$$

式中： $a(\alpha _{i}^{ST})=f(\sin \alpha _{i}^{ST},d_{s},M_{h});c(\alpha _{i}^{ST},t)=$ $\bar {G}_{i}^{\mathrm {STRC}}\boldsymbol {f}^{\mathrm {T}}\left(\sin \alpha _{i}^{\mathrm {TRC}},d_{\mathrm {r}},N_{\mathrm {h}}\right)\boldsymbol {w}_{\mathrm {h}}(t)+G_{i}^{\mathrm {STC}}$ 

设当前共有I个感知目标，则IRS接收到的I个感知目标总信号为

$$y^{\mathrm {eff}}(t)=\sum _{i=1}^{I}a(\alpha _{i}^{\mathrm {ST}})c(\alpha _{i}^{\mathrm {ST}},t)+\sum _{i=1}^{I}n_{i}^{\mathrm {eff}}(t)=\quad (a(\alpha _{1}^{ST}),a(\alpha _{2}^{ST}),\cdots ,a(\alpha _{l}^{ST}))\cdot (c(\alpha _{1}^{ST},t)\quad c(\alpha _{2}^{\mathrm {ST}},t),\cdots ,c(\alpha _{l}^{\mathrm {ST}},t))^{\mathrm {T}}+\sum _{i=1}^{l}n_{i}^{\mathrm {eff}}(t).\tag{18}$$

## 2多目标DOA估计算法

目标个数未知时，传统MUSIC算法在目标个数未知时依赖能量占比准则确定噪声子空间，但该准则在低信噪比下易出现多估问题，尤其当感知目标与IRS之间的距离远近不同时，接收信号 $y_{i}^{\mathrm {eff}}$ 的信噪比（signal to noise ratio,SNR）各不相同，在这种复杂情况下，传统MUSIC的性能将进一步下降，为解决该问题，下面介绍对传统MUSIC算法的改进.

对T个时隙进行堆叠，可得接收信号矩阵 $\boldsymbol {Y}^{\mathrm {eff}}\in \mathbf {C}^{M_{\mathrm {h}}\times T},$ 

<!-- ·88· -->

<!-- 华中科技大学学报（自然科学版） -->

<!-- 第54卷 -->

<!-- 万方数据 -->

$$Y^{eff}=(y^{eff}(1),y^{eff}(2),\cdots ,y^{eff}(T))=\quad A(\alpha ^{ST})C+N,\tag{19}$$

式中：

$$\boldsymbol {A}\left(\alpha ^{\mathrm {ST}}\right)=\left(\boldsymbol {a}\left(\alpha _{1}^{\mathrm {ST}}\right),\boldsymbol {a}\left(\alpha _{2}^{\mathrm {ST}}\right),\cdots ,\boldsymbol {a}\left(\alpha _{I}^{\mathrm {ST}}\right)\right)\in \mathbf {C}^{M_{\mathrm {h}}\times I};$$

(20)

$$\boldsymbol {C}=\begin{pmatrix}c\;(\alpha _{1}^{\mathrm {ST}},1)&c\;(\alpha _{1}^{\mathrm {ST}},2\;)&\cdots &c\;(\alpha _{1}^{\mathrm {ST}},\;T\;)\\ c\;(\alpha _{2}^{\mathrm {ST}},\;1)&c\;(\alpha _{2}^{\mathrm {ST}},\;2\;)&\cdots &c\;(\alpha _{2}^{\mathrm {ST}},\;T\;)\\ :&&&\\ c\;(\alpha _{l}^{\mathrm {ST}},\;1)&c\;(\alpha _{l}^{\mathrm {ST}},\;2\;)&\cdots &c\;(\alpha _{l}^{\mathrm {ST}},\;T\;)\end{pmatrix}\in \mathbf {C}^{l\;\times \;T};$$

(21)

$$N=\left(\sum _{i=1}^{I}n_{i}^{\mathrm {eff}}(1),\sum _{i=1}^{I}n_{i}^{\mathrm {eff}}(2),\cdots ,\right.$$

$$\left.\sum _{i=1}^{I}\boldsymbol {n}_{i}^{\mathrm {eff}}(T)\right)\in \mathbf {C}^{M_{h}\times T}.\tag{22}$$

接收信号 $\boldsymbol {Y}^{\mathrm {eff}}$ 的协方差矩阵可写为

$$R_{Y}=E[Y^{eff}Y^{effH}]=$$

$$E[(A(\alpha ^{ST})C+N)(A(\alpha ^{ST})C+N)^{H}]=$$

$$A(\alpha ^{\mathrm {ST}})R_{C}A^{\mathrm {H}}(\alpha ^{\mathrm {ST}})+\sigma _{\mathrm {n}}^{2}I_{M_{\mathrm {h}}},\tag{23}$$

式中： $\boldsymbol {R}_{C}=E\left[\boldsymbol {C}\boldsymbol {C}^{\mathrm {H}}\right]\in \mathbf {C}^{l\times l};$ $\boldsymbol {R}_{Y}=\boldsymbol {R}_{Y}^{\mathrm {H}}\in \mathbf {C}^{M_{\mathrm {h}}\times M_{\mathrm {h}}}$ 成立； $\sigma _{n}^{2}$ 为噪声信号 $N$ 的平均功率.

对矩阵 $R_{Y}$ 做特征值分解，可得

$$EIG(R_{Y})=UΣU^{H}=$$

$$\begin{pmatrix}\boldsymbol {U}_{s}&\boldsymbol {U}_{n}\end{pmatrix}\begin{pmatrix}\boldsymbol {Σ}_{\mathrm {s}}&\\ &\boldsymbol {Σ}_{\mathrm {n}}\end{pmatrix}\begin{pmatrix}\boldsymbol {U}_{\mathrm {s}}^{\mathrm {H}}\\ \boldsymbol {U}_{\mathrm {n}}^{\mathrm {H}}\end{pmatrix},\tag{24}$$

式中： $Σ$ 为 $\boldsymbol {R}_{Y}\in \mathbf {C}^{M_{\mathrm {h}}\times M_{\mathrm {h}}}$ 的特征值矩阵； $U\in \mathbf {C}^{M_{\mathrm {h}}\times M_{\mathrm {h}}}$ 为 $R_{Y}$ 的特征矩阵，U中每个列向量均是 $R_{Y}$ 对应特征值的特征向量，U中各列向量组成的空间称为总空间，且U是酉矩阵［5]； $\boldsymbol {Σ}_{\mathrm {s}}\in \mathbf {C}^{l\times l}$ 和 $U_{\mathrm {s}}\in \mathbf {C}^{M_{\mathrm {h}}\times I}$ 分别为有用信号对应的特征值矩阵和特征矩阵， $U_{s}$ 中各列向量组成的空间称为信号子空间； $Σ_{n}\in$ $\mathbf {C}^{\left(M_{\mathrm {h}}-I\right)\times \left(M_{\mathrm {h}}-I\right)}$ 和 $U_{\mathrm {n}}\in \mathbf {C}^{M_{\mathrm {h}}\times \left(M_{\mathrm {h}}-I\right)}$ 分别为噪声信号对应的特征值矩阵和特征矩阵， $U_{n}$ 中各列向量组成的空间称为噪声子空间. $\lambda =\text {vec}_{\text {diag}}(Σ)$ 表示特征值向量， $\lambda _{s}=\text {vec}_{\text {diag}}(Σ_{s})$ 和 $\lambda _{n}=\text {vec}_{\text {diag}}(Σ_{s})$ 分别表示信号和噪声特征值向量.

不失一般性，设某噪声特征值为 $\lambda _{n}^{\prime },$ ，其对应的特征向量为 $\boldsymbol {u}^{\prime }\in \mathbf {C}^{M_{\mathrm {h}}\times 1},$ ，则 $R_{Y}u^{\prime }=\lambda _{n}^{\prime }u^{\prime }$ ，把式（23）代入可得 $(A(\alpha ^{ST})R_{C}A^{H}(\alpha ^{ST})+\lambda _{n}^{\prime }I_{M_{h}})u^{\prime }=\lambda _{n}^{\prime }u^{\prime },$ 化简可得

$$(A(\alpha ^{ST})R_{C}A^{H}(\alpha ^{ST})+\lambda _{n}^{\prime }I_{M_{h}})u^{\prime }=\lambda _{n}^{\prime }u^{\prime }.$$

$$\boldsymbol {A}\left(\alpha ^{\mathrm {ST}}\right)\boldsymbol {R}_{C}\boldsymbol {A}^{\mathrm {H}}\left(\alpha ^{\mathrm {ST}}\right)\boldsymbol {u}=\mathbf {0}_{M_{\mathrm {h}}\times 1}.\tag{25}$$

等式两边同时左乘 $R_{C}^{-1}(A^{H}(\alpha ^{ST})A(\alpha ^{ST}))^{-1}A^{H}$ 进一步化简可得

$$R_{C}^{-1}(A^{H}(\alpha ^{ST})A(\alpha ^{ST}))^{-1}A^{H}(\alpha ^{ST})A(\alpha ^{ST})R_{C}.\quad \boldsymbol {A}^{\mathrm {H}}\left(\alpha ^{\mathrm {ST}}\right)\boldsymbol {u}=\mathbf {0}_{I\times 1}$$

$$(A^{H}(\alpha ^{ST})A(\alpha ^{ST})R_{C})^{-1}(A^{H}(\alpha ^{ST})A(\alpha ^{ST})R_{C})\cdot$$

$$\boldsymbol {A}^{\mathrm {H}}\left(\alpha ^{\mathrm {ST}}\right)\boldsymbol {u}=\mathbf {0}_{I\times 1};$$

$$\boldsymbol {u}^{\mathrm {H}}\boldsymbol {A}\left(\alpha ^{\mathrm {ST}}\right)=\mathbf {0}_{1\times I}.$$

上式表明了噪声特征值对应特征向量的共轭转置与目标导向矩阵的内积为零向量，注意到导向矩阵 $A^{H}(\alpha ^{ST})$ 是由感知目标的水平导向向量 $a(a_{i}^{ST})$ 组成的矩阵，该矩阵中只含有感知目标的方位角参数.因此，可以使用谱峰搜索实现对目标方位角的超分辨率估计，谱峰搜索公式如下

$$P(\alpha _{i}^{ST})=[a^{H}(\alpha _{i}^{ST})U_{n}U_{n}^{H}a(\alpha _{i}^{ST})]^{-1}.\tag{26}$$

用包含谱峰搜索的经典MUSIC算法可以实现对目标DOA的估计，但在实际场景中，如何准确估计出目标数，并从总空间中提取噪声子空间是一个难题.目前，获取目标数的方法有粗估计和精估计两种，在经典MUSIC算法中，常使用能量占比准则粗略估计信号数，从而获得噪声子空间，该准则可以表示如下

s.t. $\sum _{i}^{\hat {I}}\lambda _{i}^{2}/\sum _{i}^{M_{k}}\lambda _{i}^{2}\geq \eta ,$ 

min i $\left(\hat {I}=1,2,\cdots ,M_{\mathrm {h}}\right)$ 

式中： $\hat {I}$ 为I的估计值；η为能量门限因子，基于该准则的噪声子空间为span $\{U_{.\hat {l}+1},U_{.\hat {l}+2},\cdots ,$ $\left.U_{\cdot M_{h}}\right\}$ .当接收信号SNR很小时，使用该准则易出现多估问题.为解决该问题，采用DENCLUE-MUSIC对特征值进行分类.DENCLUE最先被Al-exander Hinneburg在文献［17］中提出，并被称为DENCLUE 1.0版本，该算法通过计算每一个数据点的密度值、吸引子和半径，再根据不同数据点吸引子和半径的关系判断数据点是否为一类.随后该作者又对DENCLEN 1.0进行了改进，在文献［18］中提出了DENLCUE 2.0版本，该版本提升了DEN-CLUE算法的运行速度，节省了时间开销.采用DENCLUE 2.0与传统MUSIC算法相结合，提出了新的DENCLUE-MUSIC算法.

在DENCLUE 2.0算法中，引入高斯核函数 $T_{Gauss}(x_{1},x_{2})$ 和密度变化度量函数 $\Delta Q_{\text {Density}}(d_{k+1},$ $\left.d_{k}\right)$ ，即

$$T_{Gauss}(x_{1},x_{2})=\frac {1}{\sqrt {2\pi }\sigma }\exp [-\frac {(x_{1}-x_{2})^{2}}{2\sigma ^{2}}],$$

式中：σ为高斯核函数的平滑因子，σ愈小，曲线愈陡峭； $x_{1}$ 和 $x_{2}$ 的微小变化就能引起函数值的很大反应，针对不同的数据特点，合理设置平滑因子能够显著提升其聚类性能.有

<!-- 第3期 -->

<!-- 韩曦，等：基于聚类改进MUSIC的多目标方向感知算法 -->

<!-- ·89· -->

<!-- 万方数据 -->

$$\Delta Q_{Density}(d_{k+1},d_{k})=\frac {d_{k+1}-d_{k}}{d_{k+1}},\tag{27}$$

式中 $d_{k+1}$ 和 $d_{k}$ 分别为第 $k+1$ 次和k迭代过程中求得的密度值．式（27）计算结果表达了相邻两次迭代过程密度值的变化程度.随着迭代次数的增加，密度值呈现单调递增且逐渐收敛的变化趋势［16]，当相邻两次的变化非常小，即 $\Delta Q_{\text {Density}}<ε$ 成立时，表明密度值已增加到局部最大值的邻域，此时终止迭代过程，ε被称为迭代门限因子.

将该算法应用于特征值的分类，结合实际应用背景，特征值向量λ中有且只有两类，即信号特征值和噪声特征值.不同SNR的接收信号对应的特征值可能变化很大，但在同一环境的同一时刻，可以认为噪声特征值变化微小或不变.因此可以把数值小且分布集中的特征值认为是噪声特征值，其他为信号特征值，由此实现信号特征值和噪声特征值的分离，进而获得感知目标个数I和噪声子空间.

DENCLUE-MUSIC算法流程由3个阶段构成．在数据预处理阶段，设置最大迭代次数K、初始吸引子 $a^{1}=\lambda$ 、密度阈值 $d_{0}$ 和 $d_{1}$  平滑因子 $σ$ 、迭代门限因子ε等参数，然后使用式（19）、式（23）和特征值分解计算特征值和特征向量，该阶段的数据标准化处理提升了聚类稳定性.

进入聚类阶段后，算法对每个特征值执行迭代优化过程.在每次迭代中，算法保存上一轮的吸引子和密度值，利用DENCLUE2.0对特征值进行精确分类，随后基于高斯核函数重新计算当前密度，得到 $d_{k}$ ，并更新吸引子位置，同时记录相邻迭代间吸引子的变化量.当密度变化小于设定阈值或达到最大迭代次数时停止迭代.完成所有特征值的迭代后，算法根据最近h次吸引子变化情况确定每个特征的聚类半径.最后通过比较不同特征对应吸引子间的距离与半径之和的关系，将满足条件的特征值归为同一类.

聚类阶段利用DENCLUE2.0对特征值进行精确分类，克服了传统能量占比准则低信噪比下的多估问题．在角度估计阶段，算法在 $-\pi /2\sim \pi /2$ 2范围内进行角度扫描，利用聚类阶段得到的聚类结果，计算每个候选角度对应的空间谱值.

最终通过识别空间谱的峰值位置，输出多目标的DOA估计结果.DNECLUE-MUSIC算法流程如下.

**输入** 接收信号 $y^{\mathrm {eff}}(t)$ 

**输出** 多目标角度估计结果

**阶段1** 数据预处理

**步骤1** 参数初始化：K， $a^{1}=\lambda$  $d_{0},d_{1},R=$ $\mathbf {0}_{M_{k}\times K},$  σ,E;

**步骤2** 用式（19）得到接收信号矩阵 $Y^{\mathrm {eff}};$ 

**步骤3** 用式（23）计算 $\boldsymbol {Y}^{\mathrm {eff}}$ 的协方差矩阵 $R_{Y};$ 

步骤4 对 $R_{Y}$ 进行特征值分解得到特征值λ和特征矩阵U.

## 阶段2 聚类

步骤1 对于每个IRS单元（共 $M_{h}$ 个）执行迭代计算，即初始化迭代次数 $k=0$ ，当满足 $k<K$ 或连续两次迭代计算的密度变化大于阈值 $\Delta Q_{\text {Density}}\left(d_{k+1},d_{k}\right)>ε$ 时，循环执行 $k=k+1$ ；此时 $d_{k-1}=d_{k},$ $a_{i}^{k-1}=a_{i}^{k};$  计算当前所有数据点相对于当前中心的高斯函数密度和 $d_{k}=$ $\frac {1}{M_{h}}\sum _{j=1}^{M_{k}}T_{\mathrm {Gauss}}(a_{i}^{k-1},\lambda _{j});$  根据各数据点的权重计算并更新中心点的位置 $\boldsymbol {a}_{i}^{k}=\sum _{j=1}^{M_{k}}\lambda _{j}T_{Gauss}(\boldsymbol {a}_{i}^{k-1},\lambda _{j})$ $\sum _{j=1}^{M_{k}}T_{Gauss}(a_{i}^{k-1},\lambda _{j});$  计算本次迭代中心点位置与前一次的相对变化率 $R_{i,k}=\vert a_{i}^{k}-a_{i}^{k-1}\vert .$ 

步骤2 确定该数据点的类半径，若 $k>h$ ，则半径取最后几次迭代变化率的和 $r=\sum _{j=k-h+1}^{k}R_{\cdot j};$ ；否则，取所有迭代次数的变化率和 $\boldsymbol {r}=\sum _{j=1}^{k}\boldsymbol {R}_{\cdot ,j}$ .遍历所有IRS单元，比较任意两个不同点迭代后确定的中心位置距离，如果两个中心点的距离小于它们各自类半径之和，那么认为这两个点属于同一类.

**阶段3** 角度估计

使用式（26）计算 $\theta =-\pi /2\sim \pi /$ 内每一个候选角度的谱峰值；根据谱峰图，输出DOA估计结果.

## 3 仿真结果分析

对所提算法进行仿真验证，并与文献［18］提出的DBSCAN改进MUSIC的聚类估计算法进行均方根误差（root mean square error,RMSE）和时间复杂度的比较，RMSE计算公式为 $σ_{\mathrm {RMSE}}=$ $\left[\frac {1}{I}\sum _{i}^{I}\left(\theta _{i}-\hat {\theta }_{i}\right)^{2}\right]^{1/2}$ ，其中 $\theta _{i}$ 表示第i个目标的实际DOA.并分析了不同参数对所提算法估计准确率的影响.仿真参数中垂直单元数 $M_{v}=16,$ ，垂直子阵数 $N_{v}=15$ ，仿真时常 $T=1000$ ，收敛阈值 $ε=0.1$ ，水平单元数 $M_{h}=16,$ ，水平子阵数 $N_{h}=15$ ，噪声标准差 $σ=0.11$ ，用户数 $K=5$ ，接受间距 $d_{r}=0.5\mathrm {\;m}$ ，最小距

<!-- ·90· -->

<!-- 华中科技大学学报（自然科学版） -->

<!-- 第54卷 -->

离 $d_{0}=100$ $m$ ，发射间距 $d_{s}=0.5$ m，最大距离 $d_{1}=$ 200m.

图2展示了特征值随SNR的变化情况．从图中可以发现，信号特征值近似指数正比于SNR，当SNR大于-10dB时，信号特征值显著大于噪声特征值，对特征值进行分类时，因为特征值有且只有信号和噪声特征值两类，对于信号特征值来说，当存在多个感知目标且与IRS的距离不同时，将导致信号特征值大小不一；但对于噪声特征值来说，由于实际环境中各反射信号同时被IRS感知单元接收，可以认为噪声功率相同，因此噪声特征值呈现出“聚类”特征，可以通过分辨出噪声特征值，则剩余特征值为信号特征值.

<!-- 103 $10^{3}$ 102 $10^{2}$ 信号 ' $10^{1}$ 噪声 $10^{\circ }$ 10°吨 -20 -10 0 10 20 信噪比／dB -->
![](https://web-api.textin.com/ocr_image/external/09076136ab71d34a.jpg)

图2 不同SNR下特征值变化曲线

当感知目标距离IRS远近不同，多个不同SNR 的信号同时出现时，可能导致某一距离较近的目标信号淹没距离较远、幅值较低的目标信号．图3(a）展示了有3个目标信号，SNR依次为10,0和-10dB时的特征值情况.可以发现：前两个特征值显著大于其他特征值，而第三个特征值对应信号的SNR很低，使其与噪声特征值差异很小，这使得MUSIC算法在低信噪比时部分失效，从而影响感知性能．图3(b）展示了使用DENCLUE-MUSIC算法对特征值进行分类的结果，前三个特征值被正确标记为信号特征值，反映了DENCLUE-MUSIC算法对特征值的分类性能稳定且效果显著.

图4对比了MUSIC,DENCLU-MUSIC与DB- $SCAN-MUSIC[19]$ ］的DOA估计误差和运行时间结果，两种改进算法的误差几乎相同，且明显优于MUSIC算法.

图5对比了MUSIC,DENCLU-MUSIC与DB-SCAN-MUSIC的运行时间.图中可以看到传统MUSIC算法时间复杂度最低，这是因为它未使用聚类操作.其他两种用聚类操作的算法对比，可以看到：本文算法的时间复杂度显著低于DBSCAN-MUSIC算法，这进一步验证了其优异性能.文献［17］也证明了DENCLUE算法的运行速度显著快于DBSCAN.

<!-- 80 60 40 20 0 （a）特征值 -->
![](https://web-api.textin.com/ocr_image/external/038f52c8e90bce53.jpg)

<!-- 80 60 信号 40 20 噪声 0 4 8 12 16 特征值索引 -->
![](https://web-api.textin.com/ocr_image/external/0e15dcc63252ecb1.jpg)

（b）谱峰值

图3 目标信号SNR分别为10,0和-10dB时的特征值和特征值聚类结果

<!-- ¹ $10^{1}$ MUSIC DBSCAN-MUSIC / $10^{-1}$ DENCLUE-MUSIC 10-^ $10^{-3}$ -15 -5 5 15 信噪比／dB -->
![](https://web-api.textin.com/ocr_image/external/c1dbe30b2613a041.jpg)

图4 本文算法与文献［19］算法性能对比

<!-- 0.4 0.3 DBSCAN-MUSIC S日／na0 0.2 DENCLUE-MUSIC 0.1 MUSIC 0.0 10 30 50 70 90 IRS单元数量 -->
![](https://web-api.textin.com/ocr_image/external/224bd9b2496c4501.jpg)

图5 本文算法与文献［19］算法时间对比

<!-- 万方数据 -->

<!-- 第3期 -->

<!-- 韩曦，等：基于聚类改进MUSIC的多目标方向感知算法 -->

<!-- ·91· -->

<!-- 万方数据 -->

## 4 结语

提出的DENCLUE-MUSIC算法解决了传统MUSIC算法低SNR时性能不稳定的问题.该算法利用信号特征值与噪声特征值的本质差异，用DENCLUE 2.0技术对特征值进行聚类，获取噪声子空间，最后通过谱峰搜索获取各目标的方向信息.相较于传统MUSIC算法，本文算法通过结合DENCLUE 2.0聚类技术大幅提升了目标数量和DOA的估计准确度.相较于经典MUSIC算法在SNR为15dB时，准确估计目标个数概率只有80％左右；本文算法在SNR不小于-12dB时，准确率稳定大于94%．此外，与DBSCAN-MUSIC算法相比，本文算法计算复杂度更低.

## 参考文献

[1] NGO TD, TRUONG X T. Socially aware robot naviga-tion framework: where and how to approach people in dynamic social environments[J]. IEEE Transactions on Automation Science and Engineering, 2022, 20(2): 1322-1336.

[2] DONG L, SUN D, HAN G, et al. Velocity-free local-ization of autonomous driverless vehicles in underground intelligent mines[J]. IEEE Transactions on Vehicular Technology,2020,69(9):9292-9303.

[3] CHOWDHURY M Z, SHAHJALAL M,AHMED S, et al. 6G wireless communication systems: applica-tions, requirements, technologies, challenges, and re-search directions[J]. IEEE Open Journal of the Commu-nications Society,2020,1:957-975.

[4] WPD I. Future technology trends of terrestrial interna-tional mobile telecommunications systems towards 2030and beyond[J]. International Telecommunication Union, Report M, 2022(1): 2516-2520.

［5］葛晓虎，喻可．基于TR的超宽带智能反射面建模与优化［J]．华中科技大学学报（自然科学版）,2023,51(3)：7-16.

［6］吴皓威，刘燕，王柳彬，等．基于用户协作干扰的IRS辅助安全传输方案［J].华中科技大学学报（自然科学版）,2023,51(3):38-46．

[7] BASAR E, DI RENZO M, DE ROSNY J,et al. Wire-less communications through reconfigurable intelligent surfaces[J]. IEEE Access, 2019, 7: 116753-116773.

[8] LIU H, YUAN X, ZHANG Y JA. Matrix-calibration-based cascaded channel estimation for reconfigurable in-telligent surface assisted multiuser MIMO[J]. IEEE Jour-nal on Selected Areas in Communications, 2020,38(11):2621-2636.

［9］李胜峰，杨欣，王伶．基于加权分数阶傅里叶变换的IRS安全通信［J].华中科技大学学报（自然科学版），2023,51(3):25-30.

[10] ZHANG H, ZHANG H, DI B, et al. Metalocaliza-tion: reconfigurable intelligent surface aided multi-user wireless indoor localization[J]. IEEE Transactions on Wireless Communications, 2021, 20(12):7743-7757.

[11] RAZEVIG VV, CHIZH MA,CHAPURSKY VV,et al. Numerical comparison of mono-static and multi-stat-ic array performance in personnel screening systems[C]// Proc of 2016 Progress in Electromagnetic Research Sym-posium (PIERS). New York: IEEE, 2016: 2137-2141.

[12] ZHANG X, WANG F, LI H. An efficient method for cooperative multi-target localization in automotive radar [J]. IEEE Signal Processing Letters, 2021, 29: 16-20.

[13] WU Q, ZHANG S, ZHENG B, et al. Intelligent re-flecting surface-aided wireless communications: a tutori-al[J]. IEEE Transactions on Communications, 2021, 69(5):3313-3351.

[14] LIU L, ZHANG S. A two-stage radar sensing approach based on MIMO-OFDM technology[C]// Proc of 2020IEEE Globecom Workshops. New York: IEEE, 2020: 1-6.

[15] SHAO X, YOU C, MA W,et al. Target sensing with intelligent reflecting surface: architecture and perfor-mance[J]. IEEE Journal on Selected Areas in Communi-cations,2022,40(7):2070-2084.

［16］史荣昌，魏丰．矩阵分析［M]．北京：北京理工大学出版社，1996．

[17] HINNEBURG A, KEIM D A. An efficient approach to clustering in large multimedia databases with noise[M]. Konstanz: Bibliothek der Universität Konstanz, 1998.

[18] HINNEBURG A, GABRIEL H H. Denclue 2.0: Fast clustering based on kernel density estimation[C]//Proc of International Symposium on Intelligent Data Analysis. Berlin: Springer, 2007:70-80.

［19］张明洋，查淞元，刘雨东．基于特征值聚类的MUSIC 算法［J]．西北工业大学学报，2023,41(3):574-578．

