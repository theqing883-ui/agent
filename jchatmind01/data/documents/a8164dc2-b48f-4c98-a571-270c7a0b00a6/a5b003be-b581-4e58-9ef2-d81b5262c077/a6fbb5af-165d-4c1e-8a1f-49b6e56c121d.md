<!-- 第42卷第1期 -->

<!-- 微波学报 -->

<!-- Vol.42 No.1 -->

<!-- 2026年2月 -->

<!-- JOURNAL OF MICROWAVES -->

<!-- Feb.2026 -->

<!-- 万方数据 -->

**文章编号**：1005-6122(2026)01-0037-09

**DOI**:10.14183/j.cnki.1005-6122.JMW24230

# 一种三角栅格排布的超宽带双极化

## Vivaldi 天线阵列设计

于大群¹，郝张成²，庄建兴¹，糜健¹

（1．南京电子技术研究所，南京210039;2．东南大学信息科学与工程学院，南京210096）

**摘要**：针对多功能一体化相控阵系统的超宽带、多极化需求，本文提出了一种三角栅格排布的超宽带双极化Vivaldi天线阵列。与传统的双极化Vivaldi天线阵列相比，阵列单元及对应收发有源通道数量可减少约15%，天线结构排布更加紧凑，研制成本大幅降低。设计平面耦合的双极化馈电结构，降低了Vivaldi天线阵列剖面高度。本天线阵列可采用金属工艺和PCB 工艺相结合来加工实现，保证天线具有良好的散热特性和高功率容量，更加适合天线轻量化集成设计。本天线通过介质挖槽、地面挖腔、短路针等方式抑制带内谐振点，最终仅需要辐射体、地面和两层介质进行馈电，即可实现3GHz~11 GHz的双极化超宽带阻抗匹配，在±30°圆锥面内驻波小于3。

**关键词**：Vivaldi阵列；超宽带；平面耦合馈电；双极化；三角栅格布阵

中图分类号：TN823.4;TN820.12

文献标志码：A

# Design of an Ultra-wideband Dual-polarized Vivaldi Array Antenna Based on

# Triangular Lattice Arrangement

YU Daqun',HAO Zhangcheng2,ZHUANG Jianxing',MI Jian¹

(1.Nanjing Research Institute of Electronics Technology, Nanjing 210039,China;

2. School of Information Science aind Engineering, Southeast University, Nanjing 210096,China)

**Abstract:**With the requirements of ultra-wideband and multi-polarization for the multifunctional integrated phased arraysys-tem, a broadband dual-polarized Vivaldi array antenna based on triangular lattice arrangement is proposed. Different from conven-tional dual-polarized Vivaldi array antennas, the proposed array with a triangular lattice offers a 15% reduction in the number of ar-ray elements and corresponding transceiving active channels,thereby reducing the cost and weight of the antenna system. In this paper,the dual-polarized Vivaldi antenna adopts a planar coupled feeding structure to reduce the profile height. Different from con-ventional Vivaldi antenna, a combination of metallic structure and PCB structure is employed to ensure good heat dissipation and high power capacity, while reducing the antenna weight and enhancing robustness. The methods of substrate grooving, ground cavi-ty etching, and shorting pins are utilized to suppress in-band resonance points. Eventually,only the metallic radiator, ground, and two dielectric feed layers are required to achieve 3 GHz~11 GHz dual-polarized ultra-wideband impedance matching,with the standing wave below 3 within the $\pm 30^{\circ }$  conical scanning range.

**Key words:** Vivaldi array; ultra-wideband; planar coupling feed; dual polarization; triangular lattice arrangement

## 0引言

现代化军用电子信息设备的工作环境越来越复杂，功能越来越完善。相控阵天线以其高增益、窄波束和快速的波束扫描能力得到青睐，随着多功能一体化相控阵系统的研发推进，对天线阵列的带宽、极化、轻量化和成本等方面提出了越来越高的要求。

超宽带工作是当前多功能一体化相控阵系统的核心指标，单天线中虽有贴片单极子［1］、电磁偶极子［2］等可实现超宽带工作，但这类天线结构尺寸偏大，并不适合作为相控阵天线单元，平面螺旋天线［3］则很少用于波束扫描阵列。基于紧耦合机理的天线阵列是当前最常用的超宽带相控阵天线形式。

基于紧耦合机理的相控阵天线原理最早于上世纪60年代被提出［4］。基于该机理的超宽带相控阵天线得到了广泛研究与应用，如连接偶极子阵列 $[5-6]$ 。近年来广泛使用的紧耦合偶极子阵列（TCDA)，不直接连接偶极子，而是采用容性耦合，抵消地面电感效应，大幅提高了天线带宽。国外的VOLAKIS JL团队 $[7-10]$ 以及国内的杨仕文教授团队 $[11-14]$ 都在TCDA

**收稿日期**：2025-08-17；修回日期：2025-10-10

**基金项目**：雷达探测感知全国重点实验室稳定支持基金项目（XXX61424022301）

**引用格式**：于大群，郝张成，庄建兴，等．一种三角栅格排布的超宽带双极化Vivaldi天线阵列设计［J]．微波学报，2026,42(1):37-45．YU Daqun,HAO Zhangcheng,ZHUANG Jianxing, et al. Design of an ultra-wideband dual-polarized Vivaldi array antenna based on triangular lattice arrangement[J]. Journal of Microwaves,2026,42(1):37-45.

<!-- 38 -->

<!-- 微波学报 -->

<!-- 2026年2月 -->

上做了大量工作。早期的TCDA阵列采用垂直的PCB 结构，其结构强度并不高。2012年，VOUVAKIS MN 团队 $[15-17]$ 在TCDA的基础上，提出了平面超宽带模块化天线（PUMA）阵列，相比于传统TCDA，具有高强度、模块化、集成度高等优势。此外，与连续电流相对偶的是连续磁流，NETO A团队［18-19］提出的连接槽阵列便是基于此原理设计而成。TCDA、PUMA和连接槽阵列的馈电和辐射结构均比较复杂，通常采取多层PCB板加工工艺实现，其制作成本较高，并且功率容量受限。Vivaldi天线是紧耦合超宽带天线的另外一种典型形式，Vivaldi天线阵列可采取垂直馈电和全金属结构［20-24]，具有宽带宽角、功率容量大等优点，但具有剖面高、体积大等缺点。

多功能一体化电子系统采取紧耦合形式超宽带天线阵列，阵列单元对应的收发有源通道数量会大幅增加，所以相应的天线成本控制也是超宽带相控阵系统设计的重要方面 $i^{[25]}$ 。通过三角栅格布阵，可以在保证栅瓣不出现的前提下增大阵列单元间距，最高可节省约15％的单元数量，从而降低成本［26］。目前报道的双极化三角栅格排布Vivaldi阵列几乎都是基于全金属工艺实现 $[27-28]$ ，这是因为基于PCB工艺拼装难度大，尤其是三角栅格布阵。在文献［27］中，为了降低高剖面全金属Vivaldi的加工难度，尤其是复杂、精细馈电结构的实现，将天线分为几部分进行了模块化的加工和拼装。

本文设计了一种正三角栅格布阵的双极化Vival-di天线阵列，采取PCB和金属工艺相结合进行天线制作，双极化Vivaldi天线辐射部分采用全金属结构，设计的双极化平面耦合馈电结构采取PCB工艺实现。天线辐射部分、PCB介质和地板采用蜂窝结构设计，实现了天线阵列的轻量化，并兼具良好的结构强度和耐功率特性。所采取的双极化平面耦合馈电结构，较传统的双极化Vivaldi天线，天线阵列剖面高度仅为 $0.26\lambda _{L}(\lambda _{L}$ 为工作频带最低频点对应的波长）。为提高平面耦合结构的馈电带宽，采取了介质挖槽、接地板挖腔、增设金属短路针等手段，抑制了腔模谐振带来的阻抗失配。相比于PUMA实现的双极化三角栅格阵列 $[29]$ ，本文提出的双极化Vivaldi天线阵列结构简单、加工成本低，具有更好的散热特性和功率容量。

## 1 双极化Vivaldi天线阵列设计

### 1.1 双极化天线阵列排布

双极化Vivaldi天线阵列采取正三角栅格布阵，图1为阵列局部示意图。正三角栅格排布的栅瓣抑制条件如式（1）所示［26]，其中 $\theta _{\max }$ 为最大扫描角，如果按最 $大扫描范围\pm 60^{\circ }$ 设计天线阵列，辐射单元的间距约为 $\lambda _{h}/\sqrt {3}$ ，其中 $\lambda _{h}$ 为工作频带最高频点对应的波长。本

节所设计的天线阵列最高工作频率为11 GHz，扫描范围为 $\pm 30^{\circ }$ ，以此可求出水平单元间距 $d_{x}\leq 21$ mm，为了避免中小规模阵列出现高副瓣，间距 $d_{x}$ 按20mm开展设计和仿真。

$$d_{x}\leq \frac {1.155\lambda _{h}}{1+\sin \theta _{\max }},d_{y}=\frac {\sqrt {3}}{2}d_{x}\tag{1}$$

式中 $\lambda _{h}$ 是最高频率对应的波长。


![](https://web-api.textin.com/ocr_image/external/154e51ee334e8394.jpg)

（a）侧视图

<!-- $\lambda _{n}/\sqrt {3}$ $\lambda _{h}/\sqrt {3}$ -->
![](https://web-api.textin.com/ocr_image/external/349a47cad1b96749.jpg)

（b）俯视图

图1 正三角形栅格排布的双极化Vivaldi天线阵列排布示意图

Fig.1 Schematic diagram of dual-polarized Vivaldi antenna

array arranged in equilateral triangular lattice

相对于正三角栅格排布，双极化Vivaldi天线阵列通常采取矩形栅格排布，具体如图2所示。水平极化和垂直极化对应的辐射部分呈“L”形状，按最大扫描 $范围\pm 60^{\circ }$ 设计天线阵列，辐射单元的间距约为 $λ_{\mathrm {h}}/2$ 工作频率为11 GHz、最大扫描范围为 $\pm 30^{\circ }$ °的矩形栅格排布的天线阵列，为避免栅瓣出现，其水平与垂直方向的单元间距可按式（2）计算得为18.18mm；同时为避免中小规模阵列出现高副瓣，取水平间距 $d_{x}$ 和垂直间距 $d_{y}$ 为17.25mm。

$$d_{x}=d_{y}\leq \frac {\lambda _{h}}{1+\sin \theta _{\max }}\tag{2}$$

综合上述分析，在最高频率下实现相同的阵列波束扫描范围时，与矩形栅格阵列排布相比，三角形栅格双极化Vivaldi天线阵列的单个辐射单元所占物理面积约增大了15％。

<!-- 万方数据 -->

<!-- 第42卷第1期 -->

<!-- 于大群，等：一种三角栅格排布的超宽带双极化Vivaldi天线阵列设计 -->

<!-- 39 -->

<!-- 万方数据 -->


![](https://web-api.textin.com/ocr_image/external/a6a86b7827fc03a1.jpg)

（a）侧视图

<!-- $\lambda _{h}/2$ $\lambda _{h}/2$ -->
![](https://web-api.textin.com/ocr_image/external/5d59858dab1e9c1e.jpg)


![](https://web-api.textin.com/ocr_image/external/cf6ed9a5f3afa672.jpg)

（b）俯视图

图2 矩形栅格排布的双极化Vivaldi天线阵列排布示意图

Fig.2 Schematic diagram of dual-polarized Vivaldi antenna

array arranged in rectangular lattice

### 1.2 双极化Vivaldi天线单元设计

本文提出的三角形栅格排布的双极化Vivaldi 天线单元结构如图3所示，双极化Vivaldi天线主要由辐射体和双极化平面馈电结构组成，其中辐射体采取全金属结构，平面馈电结构由介质板和挖腔的接地板构成。由双极化Vivaldi天线单元构成的阵列，其辐射体、平面馈电结构的下层介质及接地板均可设计为蜂窝状结构，这种结构不仅能降低天线剖面高度，还具有优异的结构强度和散热性能。

<!-- 2 Vivaldi辐射体 1 3 $h_{v}$ x $y$ 介质 定位销钉 地 -->
![](https://web-api.textin.com/ocr_image/external/68d31f509c269333.jpg)

（a）三维示意图

<!-- 耦合片 接地针 y 4 $t_{1}$ 终端开路 x 带状线 d $d_{x}$ 短路针 耦合槽 围栏 d $d_{y}$ -->
![](https://web-api.textin.com/ocr_image/external/a7f3bc0171e347f4.jpg)

（b）平面馈电结构上层印制板图

<!-- $z_{4}$ 探针 端口1 $h_{2}$ 端口2 -->
![](https://web-api.textin.com/ocr_image/external/45571520b0e0cf68.jpg)

（c）平面馈电结构下层介质和接地层示意图

图3 双极化Vivaldi 天线单元结构示意图

$Fig.3$ Schematic diagram of dual-polarized

Vivaldi antenna element structure

天线尺寸 $d_{x}\times d_{y}=17.32\mathrm {\;mm}\times 20\mathrm {\;mm}$ ，所使用的介质介电常数为2.2，天线总高度为26.15mm，其中辐射体高度 $h_{v}=19.40mm,$ ，上、下层介质厚度分别为 $t_{1}=0.25mm、t_{2}=3$ $mm$ ，地板厚度 $h_{g}=3.50mm。$ 

#### （1）双极化辐射体设计

双极化Vivaldi天线的辐射体由三块呈 $120^{\circ }$ °夹角分布的金属辐射片构成。在该单元结构内侧，辐射体间存在缝隙，通过双极化天线的馈电部分耦合完成对辐射体的激励；在该单元结构外侧，阵列中不同辐射单元的辐射体在交界处相连构成整体；辐射体底部可增加结构定位销钉，用于实际加工装配时实现辐射体与下层介质、接地板的拼装。

#### （2）平面馈电结构设计

每个极化的Vivaldi天线单元均通过Y字形耦合槽实现耦合馈电激励，Y字形耦合槽以及相应的带状线均为平面化设计，具体馈电结构如图3所示。平面馈电结构由介质层和挖腔接地板构成，介质层由上层介质和下层介质构成，上层介质的下表面分别印制了耦合片和带状线。

水平极化天线（ $y$ 方向、端口1）通过两片半圆形耦合片激励辐射体1、2间的Y字形耦合槽实现电磁波辐射或接收，两片半圆形耦合片通过金属探针与馈电同轴的内导体、接地板连接，为了实现良好阻抗匹配，其中一片半圆耦合片通过三根金属探针与接地板连接，这样就与相应Y字形耦合槽构成Marchand 巴伦 $130$ ，实现了非平衡同轴传输线到平衡槽线的变换，具体如图4所示。垂直极化天线（x方向、端口2）采取与文献［31］中类似方式，同轴馈电总口连接两路并联带状线，每路带状线的特性阻抗为100Ω，且每路带线与对应的Y字形耦合槽构成一个Marchand巴伦，由此形成的左右两侧馈电结构，分别激励辐射片2、3和辐射片1、3，最终形成的总场沿x方向分布，而y方向的场因等辐反相相互抵消。为了扩展阻抗带宽，两路带状线的末端设计为扇形开路结构，具体如图4所示。

<!-- 40 -->

<!-- 微波学报 -->

<!-- 2026年2月 -->

<!-- 水平极化天线 馈电巴伦 垂直极化天线 馈电巴伦 -->
![](https://web-api.textin.com/ocr_image/external/4b236fd889d31099.jpg)

图4 双极化Vivaldi天线单元馈电结构示意图

Fig.4 Schematic diagram of feeding structure of dual-polarized

Vivaldi antenna element

在该双极化Vivaldi天线设计过程中，为了实现良好的宽带阻抗匹配，平面馈电结构设计需要注意以下六个事项：

（1）上下层介质厚度选取。上层介质厚度为0.25mm，因为50Ω转100Ω的宽带功分结构需要较细的传输线，0.25mm的介质厚度才能使线宽不小于0.20mm，降低对印制线加工精度的要求；下层介质厚度为3mm，使得构建的平面馈电结构更接近传统Vivaldi天线垂直馈电结构中开放的馈电空间，使Y字形耦合槽电场分布在槽两侧而不是槽与地面之间，抑制高频腔模谐振，从而实现天线的有效辐射和阻抗带宽的拓展。

（2)Y字形耦合槽末端增加“六边形耦合槽”，从而构建出与传统Vivaldi天线基本相同的馈电March-and巴伦，依然保持了较好的宽带特性。

（3）端口2探针周围加金属化过孔围栏以模拟同轴线地，抵消探针带来的感性影响，从而实现了从同轴传输线到带状线的良好匹配。

（4）在单元六个角落有短路针，目的是抑制单元间能量串扰产生的谐振模式，从而实现双极化天线良好的宽角匹配。

（5）将下层介质内部尽可能挖空，只保留馈电结构及外围的六边形壁，目的是降低等效介电常数，将介质内腔体谐振、表面波等移出工作频带，提高天线在高频工作的截止频率。

（6）接地板内部挖腔设计，可提升天线阻抗匹配性能，并且实现了天线阵列的轻量化设计，具体如图3(c）所示。

## 2 双极化Vivaldi天线仿真

按照第1节中的设计思路，采用ANSYS Electron-ics Desktop 2020 R2 商用电磁仿真软件，建立双极化Vivaldi 天线单元（含周期边界）及阵列的仿真模型，针对介质挖腔、增设金属短路针等结构方案开展仿真分析，具体优化过程如下详细介绍。

### 2.1 双极化Vivaldi天线单元优化设计

#### 1）平面馈电结构介质挖槽仿真

平面馈电结构的下层介质挖槽，目的是降低等效介电常数，将高频介质内部谐振移出带外，仿真结果如图5所示。在介质挖槽前，端口1馈电时，天线高频工作频率在9.6GHz处截止；端口2的电压驻波比（VSWR）虽仍较低，但曲线不平坦，并且在9.6GHz 处有较大损耗。挖槽后，端口1的高频谐振点移出了工作频带，端口2的驻波曲线均变得平滑，保证了两个极化在3GHz~11 GHz的法向有源驻波均在2.1以下。

<!-- 5.0 4.5 端口1，介质挖槽 端口2，介质挖槽 4.0 端口1，介质不挖槽 端口2，介质不挖槽 3.5 $\approx 3.0$ 2.5 2.0 1.5 1.0 3 4 5 6 7 8 9 10 频率／GHz -->
![](https://web-api.textin.com/ocr_image/external/1a1a811a078ff87c.jpg)

图5 下层介质挖槽前后单元侧射驻波

Fig.5 Voltage standing wave ratio of element broadside before and

after grooving the lower dielectric

挖槽前截止频率低，原因在于挖槽前介质内电尺寸大，且介质有束缚电磁波的特性，导致在高频段难以有效激励耦合槽，图6为端口1在10 GHz频点激励时，下层介质中心切面的场分布。在介质挖槽前，电场指向集中分布在上下两层介质之间，而非耦合槽之间，说明平面馈电结构未对金属辐射体形成有效激励；在介质挖槽后，耦合槽周围的电场由耦合槽的一侧指向另一侧，平面馈电巴伦可实现对金属辐射体的有效激励。值得注意的是，虽然垂直极化的两个耦合槽有较强电场分布，但左、右两个巴伦为差分馈电，因此不会造成端口之间的串扰。

<!-- 万方数据 -->

<!-- 第42卷第1期 -->

<!-- 于大群，等：一种三角栅格排布的超宽带双极化Vivaldi天线阵列设计 -->

<!-- 41 -->

<!-- 万方数据 -->


![](https://web-api.textin.com/ocr_image/external/172d55c558815d79.jpg)

（a）不挖腔


![](https://web-api.textin.com/ocr_image/external/9a0b86c2edd69592.jpg)

（b）介质层内挖腔

图6 平面馈电结构介质层内电场分布／10 GHz

Fig.6 Electric field distribution in the dielectric layer of the

planar feeding structure at 10 GHz

#### 2）金属短路针影响仿真

在双极化Vivaldi天线上增设金属短路针，是为了抑制端口1在8.5GHz激励时出现的腔体谐振，仿真结果如图7所示。在没有短路针时，在8.5 GHz端口1处的有源驻波出现明显的失配点，另外两个极化的端口在低频段的驻波均较大；在加载了金属短路针之后，不仅阻抗失配点消失，且天线两个极化端口的低频段驻波也得到明显改善。

<!-- 5.0 4.5 端口1，有短路针 端口2， 有短路针 4.0 -端口1， 无短路针 端口2， 无短路针 3.5 $RASIA$ 3.0 2.5 2.0 1.5 1.0/3 4 5 6 7 8 9 10 频率／GHz -->
![](https://web-api.textin.com/ocr_image/external/b84129c706dfde84.jpg)

图7 增加金属短路针前后的驻波仿真结果

Fig.7 Simulated results of voltage standing wave ratio before

and after adding metal shorting pins

从介质内电场分布也可看出增设金属短路针的改善效果，图8为端口1在8.5GHz激励时下层介质中心切面的电场分布。可以看出，没有金属短路针时，下层介质内部出现腔体谐振；而增设的短路针正好位于谐振电场最强的位置，从而有效抑制了腔体谐振，使耦合槽两侧的电场得到有效激励。


![](https://web-api.textin.com/ocr_image/external/280f279ad3b14f3f.jpg)

（a）无短路针


![](https://web-api.textin.com/ocr_image/external/a70c06a5e525fed8.jpg)

（b）加短路针

图8 下层介质的电场分布／8.5GHz

Fig.8 Electricfield distribution in the lower dielectric at 8.5 GHz

#### 3）金属接地针影响仿真

端口1接地耦合片的接地针对低、高频截止频率有重要作用：若采取对称平行双线结构（只有一根接地针），则在低频3.5GHz、高频10.9GHz处均会出现失配点，且在高频段几乎完全失配，仿真结果如图9所示。该失配点的成因是介质内腔体谐振，而增设额外金属接地针后，工作带宽可实现3GHz~11 GHz的覆盖。

<!-- 5.0 4.5 端口1,1根接地针 端口1,3根接地针 4.0 端口1,5根接地针 3.5 $INIIR$ 3.0 2.5 2.0 1.5 1.0 3 4 5 6 8 9 10 频率／GHz -->
![](https://web-api.textin.com/ocr_image/external/f686496896720983.jpg)

图9 金属接地针对水平极化天线驻波的影响仿真曲线

Fig.9 Simulated curves of the influence of metal grounding pins on the voltage standing wave ratio of horizontally polarized antennas

图9同时也给出了不同数量的金属接地针对水平极化天线驻波的影响仿真曲线，结果表明，当金属接地针数量大于3根后，天线驻波趋势变化不大，因此后续工程样机设计中，所需金属接地针应不少于3根。

<!-- 42 -->

<!-- 微波学报 -->

<!-- 2026年2月 -->

<!-- 万方数据 -->

#### 4）围栏金属过孔影响仿真

垂直极化天线端口2的探针周围增加围栏金属过孔能有效抵消探针在下层介质中引入的等效电感，从而实现良好的阻抗匹配，仿真结果如图10所示。在没有围栏金属过孔时，端口2在大部分频点完全失配。

<!-- 5.0 4.5 端口2，有围栏 端口2，无围栏 4.0 3.5 $ISWR$ 3.0 2.5 2.0 1.5 1.0 3 4 5 6 7 8 9 10 1 频率／GHz -->
![](https://web-api.textin.com/ocr_image/external/abf45d3b9d8266f9.jpg)

图10 增加围栏金属化过孔前后的驻波仿真结果

Fig.10 Simulated results of voltage standing wave ratiobefore

and after adding fence metallized vias

除了上述结构外，其他的结构设计细节对阻抗匹配和拓展带宽也有影响，如端口1耦合片的设计、端口2终端开路扇面、耦合槽终端六边形槽、地面挖腔等。

### 2.2 双极化天线端口特性仿真优化

按照第2.1节的设计思路，在ANSYS Electronics Desktop 2020 R2商用电磁仿真软件中建立双极化Vivaldi 天线单元周期边界下的仿真模型，经过优化后的天线有源驻波如图11所示，可以看出，在双极化端口同时激励时，3 GHz~11 GHz频段内，法线有源驻波小于2.2；两个主平面扫描 $30^{\circ }$ °时，3GHz~11 GHz频段有源驻波小于2.9；两个主平面扫描 $45^{\circ }$ °时，3 GHz~10 GHz 频段有源驻波基本小于2.8。

<!-- 5.0 法向 4.5 xxe面扫描至30° ye面扫描至30° 4.0 3.5 $\approx 3.0$ $fiSi$ $2.5$ 2.0 1.5 1.0 3 4 ġ 6 8 9 10 频率／GHz -->
![](https://web-api.textin.com/ocr_image/external/16e7fa2c5b9d9f6a.jpg)

<!-- 5.0 x面扫描至45° 4.5 y面扫描至45° 4.0 3.5 $ISWR$ 3.0 2.5 2.0 1.5 1.0 3 4 5 6 8 9 10 频率 /GHz -->
![](https://web-api.textin.com/ocr_image/external/86b77a70f713984f.jpg)

（a）水平极化

<!-- 5.0 法向 4.5 -xxc面扫描至30° yoc面扫描至30° 4.0 3.5 3.0 ES1 2.5 2.0 1.5 1.0 3 4 5 6 7 8 9 10 11 频率／GHz -->
![](https://web-api.textin.com/ocr_image/external/91ba3020d111a1a3.jpg)

<!-- 5.0 4.5 xe面扫描至45° yc面扫描至45° 4.0 3.5 ≤3.0 25 2.0 1.5 1.0 3 4 5 6 へ 8 9 10 11 频率／GHz -->
![](https://web-api.textin.com/ocr_image/external/48844ff55976bea0.jpg)

（b）垂直极化

图11 双极化天线有源驻波

Fig.11 Active voltage standing wave ratio of dual-polarized antenna

双极化天线两个端口的隔离度仿真结果如图12所示，隔离度在3.75 GHz及以上频段大于10dB，在4.5 GHz及以上频段大于15dB。

<!-- 0 法向 -5 xxc面扫描至30° yx面扫描至30° -10 -15 第／昼被医 20 30 -35 -40 3 4 5 6 7 8 9 10 11 频率／GHz -->
![](https://web-api.textin.com/ocr_image/external/df586cab67ef8433.jpg)

图12 天线两个端口的隔离度曲线

Fig.12 Isolation curve of the two ports of the antenna

### 2.3 双极化天线辐射特性仿真

按2.1节完成优化的双极化Vivaldi天线单元模型建立 $9\times 9$ 阵列，仿真模型如图13所示，完成阵列辐射特性仿真后，通过激励阵列中心单元的端口可得到阵中单元增益、波瓣等辐射特性。

<!-- 第42卷第1期 -->

<!-- 于大群，等：一种三角栅格排布的超宽带双极化Vivaldi天线阵列设计 -->

<!-- 43 -->


![](https://web-api.textin.com/ocr_image/external/19e4dea791fab55b.jpg)

图13 双极化Vivaldi天线阵面模型（ $9\times 9$ ）

Fig.13 Dual-polarized Vivaldi antenna array model $9\times 9$ )

主极化增益随频率变化曲线如图14所示，双极化Vivaldi天线两个主极化增益在整个频带内变化趋势与理论值基本吻合。天线的法向增益在4.5GHz~11 GHz 频段内接近口径的最大增益理论值，但在低频处，由于两极化端口间隔离度不高，部分能量被另一正交极化天线端口吸收，导致天线增益略差。

<!-- 10 5 0 口径最大理论增益 法向 AP/ -5 -10 -15 -20 3 4 5 6 8 9 10 频率／GHz -->
![](https://web-api.textin.com/ocr_image/external/3cd5d015b5bb0ffb.jpg)

（a）水平极化

<!-- 10 5 0 口径最大理论增益 法向 第／糕 -10 -15 203 4 5 6 8 频率／GHz 9 10 -->
![](https://web-api.textin.com/ocr_image/external/a75a4a6197a633cc.jpg)

（b）垂直极化

图14 双极化天线增益曲线

Fig.14 Gain curves of dual-polarized antenna

基于 $9\times 9$  规模天线阵列仿真，可获取阵中单元三维立体波瓣和两维切面波瓣。两极化的阵中单元波瓣在3dB范围内无“凹坑”，具备良好空间覆盖性，如图15所示。阵中单元在4GHz、7 GHz和11 GHz三个频点处的主极化和交叉极化波瓣如图16所示。由图可知，天线法向波瓣对称性良好，具有良好的前后比和交叉极化电平，交叉极化电平保持在-19 dB以下。

<!-- 增益／dB Max:6.3 10 0 -10 -20 -30 40 50 Min:-43.7 -->
![](https://web-api.textin.com/ocr_image/external/ed5f5613a57e0e3e.jpg)

<!-- 0 θ) 10 $-120$ 0 φ() 120 -->
![](https://web-api.textin.com/ocr_image/external/a8be10c98052f283.jpg)

（a）水平极化

<!-- 增益／dB Max:6.4 10 0 -10 -20 -30 40 Min:-36.2 -->
![](https://web-api.textin.com/ocr_image/external/a8ad0a1b1f5be0f6.jpg)

<!-- θ(°) 10 20 -120 0 φ(°) 120 -->
![](https://web-api.textin.com/ocr_image/external/0aad09017784bf27.jpg)

（b）垂直极化

图15 阵中单元波瓣三维波瓣／7 GHz

Fig.15 Three-dimensional radiation patterns of the element in the array at 7 GHz

<!-- 0 -10 -20 电／-E -30 -40 端口1-H面主极化 端口日顺交叉极化 -50 端口1-E面主极化 端口1-E面交叉极化 -60 -200 -150 -100 -50 0 50 100 150 200 角度／(°） -->
![](https://web-api.textin.com/ocr_image/external/d498fa7fee4072de.jpg)

<!-- 0 -10 -20 / -30 -50 端口2E面主极化 -60 端口2-E面交叉极化 端口2H面主极化 -70 端2-H面交叉极化 -200 -150 -100 -50 0 50 100 150 200 角度／(） -->
![](https://web-api.textin.com/ocr_image/external/8bc810b65e228a92.jpg)

(a)4 GHz

<!-- 万方数据 -->

<!-- 44 -->

<!-- 微波学报 -->

<!-- 2026年2月 -->

<!-- 0 -10 -20 -30 / -40 -50 -60 端口1H面主极化 端口H面交叉极化 -70 端口1-E面主极化 端口1E面交叉极化 -80 -200 -150 -100 -50 0 50 100 150 200 角度／(°） -->
![](https://web-api.textin.com/ocr_image/external/9bf60ecf3147d215.jpg)

<!-- 0 -10 -20 -30 第／-E -40 -50 -60 端口2E面主极化 端口2-E面交叉极化 -70 端口2H面主极化 端口2H面交叉极化 -80 -200 -150 -100 -50 0 50 100 150 200 角度／(°） -->
![](https://web-api.textin.com/ocr_image/external/153db427e20ce790.jpg)

(b) 7 GHz

<!-- 0 -10 -20 -30 第／ -40 -50 端口1-H面主极化 -60 端口1H面交叉极化 端口I-E面主极化 -70 端口HE面交叉极化 -200 -150 -100 -50 0 50 100 150 200 角度／(） -->
![](https://web-api.textin.com/ocr_image/external/500050ea95690758.jpg)

<!-- 0 -10 -20 -30 / -40 -50 端口2-E面主极化 -60 端口2-E面交叉极化 端口2-H面主极化 -70 端口2H面交叉极化 -200 -150 -100 -50 0 50 100 150 200 角度／(°） -->
![](https://web-api.textin.com/ocr_image/external/92762d38c90f2e93.jpg)

(c) 11 GHz

图16 双极化天线阵中单元波瓣

Fig.16 Co-polarization and cross-polarization radiation patterns

of the element in the array at three frequency points

## 3结论

本文针对多功能一体化相控阵系统的超宽带、多极化需求，提出并设计了一种三角栅格排布的超宽带双极化Vivaldi天线阵列。与传统矩形栅格排布的双极化Vivaldi天线阵列相比，阵列单元及对应收发有源通道数量可减少约15％。该双极化天线采用平面耦合馈电结构，天线剖面高度仅为0.26λ1。通过介质挖槽、接地板挖腔、增设金属短路针和接地针等方式，优化天线馈电结构中电场分布并抑制带内谐振点，最终仅通过辐射体、地面和两层介质进行馈电，即可实现3 GHz~11 GHz的双极化超宽带阻抗匹配，且在±30°圆锥面内有源驻波小于3。该双极化Vivaldi天线阵列结构简单、加工成本低，采用PCB与金属工艺相结合进行天线制作：天线辐射部分采用全金属结构，所设计的双极化平面耦合馈电结构通过PCB工艺实现。天线辐射部分、PCB介质和地板均可采用蜂窝结构，既实现了天线阵列的轻量化设计，又具备良好的结构强度和耐功率特性。

## 参考文献

〔1〕 FOUDAZI A, HASSANI H R, NEZHAD S M. Small UWB planar monopole antenna with added GPS/GSM/WLAN bands[J]. IEEE Transactions on Antennas and Propaga-tion,2012,60(6):2987-2992

〔2〕 GE L,LUK K M. A magneto-electric dipole for unidirec-tional UWB communications[J]. IEEE Transactions on An-tennas and Propagation, 2013, 61(11):5762-5765

〔3〕 ALWAN E A, SERTEL K, VOLAKIS J L. A simple equiva-lent circuit model for ultrawideband coupled arrays [J]. IEEE Antennas Wireless Propagation Letters,2012,11: 117-120

〔4〕 WHEELER H A. Simple relations derived from a phased-array antenna made of an infinite current sheet[J]. IEEE Transac-tions on Antennas and Propagation, 1965, 13:506-514

〔5〕 NETO A, CAVALLO D, GERINI G, et al. Scanning per-formances of wideband connected arrays in the presence of a backing reflector[J]. IEEE Transactions on Antennas and Propagation,2009,57(10):3092-3102

〔6〕 CAVALLO D, NETO A, GERINI G,et al. A 3 to 5 GHz wideband array of connected dipoles with low cross polariza-tion and wide-scan capability[J]. IEEE Transactions on An-tennas and Propagation, 2013,61(3):1148-1154

〔7〕 DOANE J P, SERTEL K, VOLAKIS J L. A wideband, wide scanning tightly coupled dipole array with integrated balun (TCDA-IB)[J]. IEEE Transactions on Antennas and Propagation, 2013,61(9):4538-4548

<!-- 万方数据 -->

<!-- 第42卷第1期 -->

<!-- 于大群，等：一种三角栅格排布的超宽带双极化Vivaldi天线阵列设计 -->

<!-- 45 -->

〔8〕 KASEMODEL J A, CHEN C C,VOLAKIS J L. Wideband planar array with integrated feed and matching network for wide-angle scanning[J]. IEEE Transactions on Antennas and Propagation,2013,61(9):4538-4548

〔9〕 YETISIR E, GHALICHECHIAN N,VOLAKIS J L. Ultraw-ideband array with 70° scanning using FSS superstrate[J]. IEEE Transactions on Antennas and Propagation, 2016,64(10):4256-4265

〔10〕ZHONG J,JOHNSON A, ALWAN E A, et al. Dual-linear polarized phased array with 9:1 bandwidth and 60° scanning off broadside[J]. IEEE Transactions on Antennas and Prop-agation,2019,67(3):1996-2001

〔11〕ZHANG H,YANG S W,CHEN Y,et al. Wideband dual-polarized linear array of tightly coupled elements[J]. IEEE Transactions on Antennas and Propagation,2018,66(1): 476-480

〔12〕ZHANG H,YANG SW,XIAO S,et al. Low-profile,light-weight, ultra-wideband tightly coupled dipole arrays loaded with split rings [J]. IEEE Transactions on Antennasand Propagation, 2019,67(6):4257-4262

〔13〕WANG B, YANG S W, ZHANG Z, et al. A ferrite-loaded ultralow profile ultra wideband tightly coupled dipole array [J]. IEEE Transactions on Antennas and Propagation, 2022,70(3):1965-1975

〔14〕WANG B, YANG S W, CHEN Y, et al. Low cross-polari-zation ultra wideband tightly coupled balanced antipodal di-pole array[J]. IEEE Transactions on Antennas and Propa-gation,2020,68(6):4479-4488

〔15〕HOLLAND S S, VOUVAKIS M N. The planar ultrawide-band modular antenna (PUMA) array[J]. IEEE Transac-tions on Antennas and Propagation, 2012,60(1):130-140

〔16〕HOLLAND S S,SCHAUBERT D H,VOUVAKIS M N. A 7-21 GHz dual-polarized planar ultrawideband modular an-tenna (PUMA) array[J]. IEEE Transactions on Antennas and Propagation, 2012,60(10):4589-4600

〔17〕 LOGAN J T,KINDT R W,LEE M Y,et al. A new class of planar ultrawideband modular antenna arrays with improved bandwidth[J]. IEEE Transactions on Antennas and Propa-gation,2018,62(2):692-701

〔18〕 SYED W H,CAVALLO D, SHIVAMURTHY H T,et al. Wideband, wide-scan planar array of connected slots loaded with artificial dielectric superstrates[J]. IEEE Transactions on Antennas and Propagation, 2016, 64(2):543-553

〔19〕 CAVALLO D,SYED W H, NETO A. Connected-slot array with artificial dielectrics: a 6 to 15 GHz dual-pol wide-scan prototype[J]. IEEE Transactions on Antennas and Propaga-tion,2018,66(6):3201-3206

〔20〕 CHIO T H, SCHAUBERT D H. Parameter study and design of wide-band widescan dual-polarized tapered slot antenna arrays[J]. IEEE Transactions on Antennas and Propaga-tion,2000,48(6):879-886

〔21〕LIU Z,CHEN Y, YANG S. In-band scattering cancellation techniques for Vivaldi antenna array[J]. IEEE Transactions on Antennas and Propagation, 2022, 70(5):3411-3420

〔22〕 FANG S G,QU S W,YANG S W,et al. Low-scattering X-band planar phased Vivaldi array antenna[J]. IEEE Transac-tions on Antennas and Propagation, 2023, 71(3):2809-2813

〔23〕KINDT R W, PICKLES W R. Ultrawideband all-metal flared-notch array radiator[J]. IEEE Transactions on Anten-nas and Propagation, 2010, 58(11):3568-3575

〔24〕YAN JB, GOGINENI S, CAMPS-RAGA B,et al. A dual-polarized 2-18 GHz Vivaldi array for airborne radar measure-ments of snow [J]. IEEE Transactions on Antennas and Propagation,2016,64(2):781-785

〔25〕于大群，郝张成，孙磊，等．一种基于去斜技术的低成本宽带数字阵列天线设计与实现［J]．微波学报，2024，40(5):21-28

YU D Q, HAO Z C, SUN L, et al. A dlesign and imple-mentation of low cost wideband digital array antenna based on stretch processing technique[J]. Journal of Microwaves, 2024,40(5):21-28

〔26〕BHATTACHARYYA A K. Phased array antennas [M]. Hoboken:Wiley Press,2006

〔27〕 PFEIFFER C, MASSMAN J, STEFFEN T. 3-D printed me-tallic dual-polarized Vivaldi arrays on square and triangular lattices[J]. IEEE Transactions on Antennas and Propaga-tion,2021,69(12):8325-8334

〔28〕KINDT R W,BINDER B T. Dual-polarized Vivaldi array on a triangular lattice[J]. IEEE Transactions on Antennas and Propagation, 2021,69(4):2083-2091

〔29〕 KINDT R W, BINDER B T. Dual-polarized planar-printed ultrawideband antenna array on a triangular grid[J]. IEEE Transactions on Antennas and Propagation, 2020,68(8): 6136-6144

〔30〕FRANK B. Gross,frontiers in antennas: next generation de-sign &engineering[M]. New York:McGraw-Hill Press,2011

〔31〕于大群，孙磊，吴鸿超．一种高隔离低交叉极化等相位中心双极化开槽天线设计［J]．微波学报，2020,36(3):26-30

YU D Q,SUN L,WU H C. A high isolation low cross-po-larization coincident phase center dual polarized flared notch antenna[J]. Journal of Microwaves, 2020,36(3):26-30

**于大群** 男，1981年生，博士，正高级工程师。主要研究方向：宽带有源相控阵系统、数字阵列天线、通信体制相控阵天线等。E-mail:yulipii@163.com

**郝张成** 男，1976年生，博士，教授，博士生导师。主要研究方向：微波毫米波阵列天线、电路与系统、亚毫米波和太赫兹理论与技术等。

<!-- 万方数据 -->

