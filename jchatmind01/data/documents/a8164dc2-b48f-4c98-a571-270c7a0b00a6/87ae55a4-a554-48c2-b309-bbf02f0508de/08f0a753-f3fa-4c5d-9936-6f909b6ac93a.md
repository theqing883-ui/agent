<!-- **第23卷第6期** -->

<!-- **航空动力学报** -->

<!-- Vol.23 No.6 -->

<!-- 2008年6月 -->

<!-- **Journal of Aerospace Power** -->

<!-- June 2008 -->

**文章编号**：1000-8055(2008)06-1131-04

# 基于RTWEC的控制算法自动代码生成技术

陆军，郭迎清，王斌正

（西北工业大学 动力与能源学院，西安710072）

摘 要：研究基于实时工作间嵌入式代码生成器（Real-time workshop embedded coder,RTWEC）的航空发动机复杂控制算法自动生成可执行代码技术，介绍原理及其应用方法.针对某型航空发动机控制器多变量控制算法的手动编程和自动生成代码进行电子控制器半实物仿真并比较分析.结果说明该方法简单、易行，有效降低了程序编制、查错和调试的工作量，缩短了研制周期，可以广泛用于控制器复杂算法从设计仿真到具体实现的开发过程.

关键词：航空、航天推进系统；航空发动机控制；复杂控制算法；自动代码生成；实时工作间嵌入式代码生成器（RTWEC)；半实物仿真

中图分类号：V233.7 **文献标识码：**A

# Automatic code generation technology for control

## method based on RTWEC

LU Jun, GUO Ying-qing, WANG Bin-zheng

(School of Power and Energy, Northwestern Polytechnical University,Xi'an 710072,China)

**Abstract**: This paper analyzed automatic code generation technology for complex control of aeroengine with real-time workshop embedded coder(RTWEC), and described the princi-ple and applied method. Automatically generated and manually programmed codes of an areoengine controller's multi-variable control method were comparatively analyzed by Hard-ware in-the-loop simulation(HILS) of electronic controller. The results show that this feasi-ble technology could reduce the workload of programming,checking,debugging, and also shorten the development cycle, so it can be widely used in the development process of con-troller's complex algorithm from design and simulation to implementation.

**Key words**: aerospace propulsion system; aeroengine control; complex control method; automatic code generation; real-time workshop embedded coder (RTWEC); hardware in-the-loop simulation

近年来，随着微电子技术的不断发展，全权限数字电子控制FADEC技术已是国内外航空发动机控制领域发展的必然趋势［1-2]．由于航空发动机结构的高度复杂性以及对其性能要求的逐步提高，促使相应的控制算法，特别是多变量控制、故障诊断等［3］变得更加复杂．而对于复杂控制算法设计与仿真，目前多采用先进的设计仿真平台软件Matlab/Simulink[4]，通过数字仿真验证后，再转化为电子控制器中可执行程序进行半实物仿真验证，这也是航空发动机控制系统研制过程中的关键一步，传统做法是需要将设计的控制算法手动转化为C或其它语言，这就需要熟悉软件的编程人员，并使大量工作用在程序的编制、查错、调试、验证上面，明显增加工作量，延长研制周期；此

**收稿日期**：2007-06-19；修订日期：2007-09-06

基金项目：西北工业大学研究生创业种子基金

**作者简介：陆军（1981-)，男，上海人，博士生，主要从事航空发动机控制与仿真．E-mail**:npu-huoye@mail.nwpu.deu.cn．

<!-- 万方数据 -->

<!-- 1132 -->

<!-- 航空动力学报 -->

<!-- 第23卷 -->

外，手动编制的代码良莠不齐，降低了软件运行的可靠度，提高了代码错误的风险性.

在航空发动机控制领域里，电子控制器主要采用嵌入式系统来实现，那么其控制算法需以嵌人式代码格式编写、编译.因此，本文将主要研究基于嵌入式代码生成器的航空发动机复杂控制算法自动生成可执行代码技术，介绍其原理以及应用方法，通过实例对自动生成代码和手动编程进行比较分析，结果说明本文提出的自动代码生成方法简单、易行，有效降低了程序编制、查错和调试的工作量，缩短了研制周期，可以广泛用于控制器复杂算法从设计仿真到具体实现的开发过程.

### 1 嵌入式代码自动生成简介［5］

RTWEC是基于Matlab/Simulink的自动代码生成环境实时工作间（Real-time workshop，RTW）中一个独立的扩展模块.它能直接从Sim-ulink模型中自动生成优化的、可移植的、自定义的产品级应用C代码，并根据目标配置自动生成高度嵌入式系统实时应用程序.

典型自动生成嵌入式代码的核心框架为一个主循环程序，不断迭代执行着后台任务或空任务并检测终止条件，而核心任务即Simulink模型框图中真正实现部分的主体代码通过一个定时器周期性中断调用函数rtOneStep来完成.其运行情况取决于模型是单采样率还是多采样率，下面的伪代码显示了在单采样率程序中的rtOneStep 框架：

void rtOneStep(void)

{

**检查中断溢出或者其它错误；**

开启中断；

**模型单步执行；**

}

由此我们可以看出，相比快速原型化类型代码而言，其框架结构十分简单，这也为程序移植提供了很大的便利性，不需要对其做很大修改就可直接调用.

### 2 控制算法代码自动生成流程

以LQG/LTR多变量鲁棒控制算法［3]（图1）为例，说明其从自动生成代码到平滑移植入电子控制器C语言程序的操作流程，如下所示：

1）安装RTWEC V6.6：需要 Matlab R2007a，Simulink V6.6,RTW V6.6，编译器推荐使用

Visual $C/C++6.0$ 以上版本；

2）在Simulink下将设计完成的算法模型进行离散化、反归一化等准备工作；

3）简化该部分与其他模型的数据流接口，并使其转化为子模型；

4）设置RTW参数选项：积分求解器为fixed-step；目标系统文件为ert.tlc(Visual $C/C++$ pro-ject makefile only for RTWEC)，语言为 $C;$ ；模板编联文件为ertmsvc.tmf；选中Single output/update function，使输出与更新函数合并在一起；选中Generate an example mnain program→BareBoard-Example，既以此生成可执行程序，也可作为主程序框架参考；数据访问接口为None；其它为默认设置；

5）通过RTW→Build Subsystem 进行自动代码生成；

6）移植控制算法代码：将相关函数、变量的声明与定义源代码文件包含进电子控制器程序工程；对初始化、执行函数进行相应调用；增加数据流接口代码部分.

总体情况如以上步骤所述，不同设置会产生略微不同的代码，如优化参数选项、注释内容、内存设置、积分求解器、初始化方案等.

<!-- B K-u $\rightarrow$ K-u Ku $N_{\mathrm {LC}},P_{36}$ $-1$ 4 $K_{t}$ Integralor K $K_{c}$ $\text {FM}$ A C -->
![](https://web-api.textin.com/ocr_image/external/4cb8bb28b276c19f.jpg)

图1 Simulink下LQG/LTR控制算法模型图

Fig.1 LQG/LTR controller model in Simulink

### 3 实时仿真验证

为了确认自动生成代码的有效性，包括精确度、执行效率两方面，将在半实物仿真环境中与手动编写代码进行比较验证.

#### 3.1 硬件环境

半实物仿真环境主要以PC104为核心的发动机电子控制器、以工控机为发动机仿真计算机的硬件框架结构组成闭环回路；其双机同步方式为外触发定时中断，由工控机向PC104触发，周期为25ms;A/D,D/A通道主要传递信号为：高**压转子转速** $N_{\mathrm {HC}}$ ，低压转子转速 $N_{\mathrm {LC}}$ ，高度H，马赫数Ma，主供油量 $W_{\mathrm {FM}}$ 加力供油量 $\boldsymbol {W}_{\mathrm {FA}}$ ，尾喷**口面积** $A_{8}$ ；其结构如图2所示：

<!-- 万方数据 -->

<!-- **第6期** -->

<!-- **陆 军等：基于RTWEC的控制算法自动代码生成技术** -->

<!-- 1133 -->

<!-- 电子控制器部分 仿真发动机部分 PC104 工业控制计算机 外触发 $\{\}_{\text {习}}$ 定时中断 ✓ $A/D$ D/A $4$ $\mathrm {M}^{*}$ $W$ PM511P $PM511P$ PCL-812PG 数据采集卡 数据采集卡 $N_{11}N_{10}H_{10}MaP_{V_{0}}$ -->
![](https://web-api.textin.com/ocr_image/external/f2321b67f00027ab.jpg)

**图2 实时仿真系统实物结构图**

$Fig.2$  Hardware frame with real-time simulation system

#### 3.2 软件环境

仿真计算机中发动机模型程序为C+＋语言，以Windows为平台，并通过VisualC/C++ 6.0平台编写监控界面；电子控制器为C语言，以DOS系统为平台.

发动机电子控制器程序主要由控制规律、控制算法、输入输出、数据记录、错误检测等基本功能组成.在其它功能保持不变的情况下，以控制算法部分代码为主，将在Matlab/Simulink 下设计出来的LQG/LTR多变量鲁棒控制算法，分别通过自动生成代码和手动编写代码进行编译、链接成电子控制器执行程序进行比较.

#### 3.3 仿真结果

##### 3.3.1 精确度情况

在地面状态下，即高度 $H=0$ km，马赫数 $Ma=0$ ，初始主供油量 $W_{FM}=4$ $500$ $kg/h$ ，加力供油量 $W_{FA}=0kg/h$ ，尾喷口面积 $A_{8}=0.2602m^{2}.$ 

仿真情况包括加减速、加力接通、切断、中间稳定等各种状态，工作路线如表1所示．

仿真结果如图3所示，其中Y轴为手动代码和自动代码情况下各自五组数据的参数百分比，即每组数据与该组最大值之比：低压转子转速 $N_{\mathrm {LC}}$ ，高压转子转速 $N_{\mathrm {HC}}$ ，尾喷管面积 $A_{8}$ ，主供油量 $\boldsymbol {W}_{\mathrm {FM}}$ ，涡轮落压比 $P_{36},X$ 轴为时间刻度，以25ms为执行周期，从图中无法区分各对应数据的参数变化，说明两者结果的一致性；再通过相对误差比较来确定其精确度，如图4所示，其中Y轴为两种情况下五组数据的相对误差，即每组误差与该组最大值之比，从图中可以看出，除了在3s时刻左右，即加力接通并加速时刻，各参量有 $10^{-4}$ 级别误差之外，其他时刻几乎没有误差.对于存在的极小误差，通过分析代码可知，出现这种情况的原因是初始化方案的细微区别在运行状态转换时刻所引起的，但这类情况对于实际航空发动机电子控制器的要求可以忽略不计.由此，可以说明两种代码所执行结果的精确度是相同的.

**表1 控制系统仿真工作线路表**

**Table 1 Work line of control system simulation**

<table border="1" ><tr>
<td>时刻t/s</td>
<td>油门杆角度$P_{LA}/(^{\circ })$（情况）</td>
<td>加力<br>开启</td>
<td>发动机<br>运行状态</td>
</tr><tr>
<td>0</td>
<td>70 （加速并稳定）</td>
<td>否</td>
<td>最大</td>
</tr><tr>
<td>3</td>
<td>130 （加力接通后加速）</td>
<td>是</td>
<td>最大加力</td>
</tr><tr>
<td>6</td>
<td>80 （减速并稳定）</td>
<td>是</td>
<td>小加力</td>
</tr><tr>
<td>9</td>
<td>70 （加力切断后减速）</td>
<td>否</td>
<td>最大</td>
</tr><tr>
<td>12</td>
<td>60 （减速并稳定）</td>
<td>否</td>
<td>中间稳定</td>
</tr></table>

<!-- 1.05 1.00 0.95 $M^{\prime }M$ 出 0.90 0.85 $x^{\prime }(\mathbf {M})$ $N_{\mathrm {ic}}(\mathrm {\;A})$ 0.80 $N$ $N_{\mathrm {BC}}(\mathrm {\;A})$ 0.75 $P_{w}(M$ 0.70 0 2 4 6 8 10 12 14 时间刻度t/s -->
![](https://web-api.textin.com/ocr_image/external/98f30f107796e35b.jpg)

图3 两种情况下各参数变化曲线图

Fig.3 Parameters variety in two ways

<!-- 5 $W_{m}$ 4 $A$ $\{\}_{\text {2}}$ 3 $\downarrow$ $N$ H0v出尔蠡 $HC$ 2 $p_{*}$ 0 -2 -3 0 2 4 6 8 时间刻度t/s -->
![](https://web-api.textin.com/ocr_image/external/cb9a1b852e543c37.jpg)

图4 两种情况下各参数精确度比较曲线图

Fig.4 Compare with parameters variety in two ways

<!-- 万方数据 -->

<!-- 1134 -->

<!-- 航空动力学报 -->

<!-- 第23卷 -->

##### 3.3.2 执行效率情况

一般情况下，在DOS系统中TIMER类中断函数只能精确到54ms级别，而此控制算法实际运行在更小级别，主要视硬件配置不同而不同，因此这类方法无法对执行代码进行精确计时，除非对系统上的8253计时器重新编程，但实现难度较大［6]．由此，次选方案是在Windows平台下运行比较，通过调用QueryPerformanceFrequency(）和QueryPerformanceCounter(）函数来实现精确计时［7]，其计时级别为 $1\mu s$  级别，测试PC机主要硬件配置为双核3.4G CPU．

执行结果如图5和图6所示，自动代码平均执行时间为 $2.0020\times 10^{-6}\mathrm {\;s}$ ，手动代码为1.4619 $\times 10^{-6}\mathrm {\;s}$ ，平均差值为5.401 $3\times 10^{-7}\mathrm {\;s}$ ，自动代码高于手动代码36.95%，显然前者比后者的执行时间要长，其原因在于自动生成代码偏重于编程规范性以及兼容性要求，部分实现方式也有所不同，则相对于精简的手动代码损失了一定的执行效率，但该结果是满足实际要求的.

<!-- 7 手动代码执行时间 6 自动代码执行时间 5 4OI回 4 3 2 1 0 2 4 6 8 10 12 14 16 18 时间刻度t/s -->
![](https://web-api.textin.com/ocr_image/external/a207d8a2851ebf1c.jpg)

图5 两种情况下执行时间曲线图

Fig.5 Runtime in two ways

<!-- 6 OIPS $Er/10^{-4}s$ 执行时间比较误差 4 2 0 -2 x0 5 10 15 时间刻度t/s -->
![](https://web-api.textin.com/ocr_image/external/2a977cc53a12fddd.jpg)

图6 两种情况下执行时间比较曲线图

Fig.6 Compare with runtime in two ways

### 4结论

研究了针对航空发动机复杂控制算法的自动可执行代码生成技术，通过半实物仿真对手动代码与自动生成代码进行了比较分析：①在精确度方面，两者几乎没有误差，完全满足要求；②在执行效率方面，后者略低于前者，按实际需要是可以接受的.这些结果基本源自两种代码在实现思想上保持一致，而具体实现上有所区别.此外，从开发时间上考虑，后者明显快于前者，而且其规范的代码开发，即满足航空机载软件开发标准：DO-178B，为后期软件可靠性验证奠定了坚实基础［8]．

总之，该方法可以广泛应用于控制算法从设计仿真到具体实现的开发过程，具有简单、易行、有效等特点，让控制工程师从繁琐的软件编制任务中解脱出来，而专心于控制系统的设计工作.

### 参考文献：

[1] Robert J M Jr. USAF gas turbine trends into the next century[R]. ISABE-97-7157,1997.

［2］张绍基．航空发动机控制系统的研发与展望［J]．航空动力学报，2004,19(3):375382．ZHANG Shaoji. A review of aeroengine control system [J].Journal of Aerospace Power,2004,19(3):375-382.

［3］王永庭，郭迎清，王海泉，航空发动机非完全恢复的LQG／LTR控制系统设计［J]．航空动力学报，2007,22(3):485-489.

WANG Yongting,GUO Yingqing,WANG Haiquan. De-sign of non-fully recover LQG/LTR control system of aeroengine[J]. Journal of Aerospace Power,2007,22(3):485-489.

[4] Rabbath C A, Bensoudane E. Real-time modeling and simulation of a gas-turbine engine control system[R]. AIAA 2001-4246,2001.

［5］杨涤，李立涛，杨旭，等，系统实时仿真开发环境与应用［M]．北京：清华大学出版社，2002．

［6]Michael Abrash．图形程序开发人员指南［M]．北京：机械工业出版社，1997．Michael Abrash. Graphics programming black book[M]. Beijing:China Machine Press,1997.

［7］郭占社，孟永钢，苏才钩，等．基于Windows的精确定时技术及其在工程中的应用［J]．哈尔滨工业大学学报，2005，(12):1717-1720.

GUO Zhanshe, MENG Yonggang, SU Caijun,et al. Windows based precise timing technology and its engi-neering applications[J]. Journal of Harbin Institute of Technology,2005,(12):1717-1720.

**［8］李同泽．机载软件合格审定的方法［J]．航空标准化与质**量，1996,(06):32-33．

LI Tongze. A method for certification of airborne software [J]. Aeronautic Standardization & Quality,1996,(06): 32-33.

<!-- 万方数据 -->

