---
name: ChronoVault Design System
colors:
  surface: '#f9f9ff'
  surface-dim: '#d8d9e3'
  surface-bright: '#f9f9ff'
  surface-container-lowest: '#ffffff'
  surface-container-low: '#f2f3fd'
  surface-container: '#ecedf7'
  surface-container-high: '#e6e7f2'
  surface-container-highest: '#e1e2ec'
  on-surface: '#191b23'
  on-surface-variant: '#424754'
  inverse-surface: '#2e3038'
  inverse-on-surface: '#eff0fa'
  outline: '#727785'
  outline-variant: '#c2c6d6'
  surface-tint: '#005ac2'
  primary: '#0058be'
  on-primary: '#ffffff'
  primary-container: '#2170e4'
  on-primary-container: '#fefcff'
  inverse-primary: '#adc6ff'
  secondary: '#00687a'
  on-secondary: '#ffffff'
  secondary-container: '#57dffe'
  on-secondary-container: '#006172'
  tertiary: '#924700'
  on-tertiary: '#ffffff'
  tertiary-container: '#b75b00'
  on-tertiary-container: '#fffbff'
  error: '#ba1a1a'
  on-error: '#ffffff'
  error-container: '#ffdad6'
  on-error-container: '#93000a'
  primary-fixed: '#d8e2ff'
  primary-fixed-dim: '#adc6ff'
  on-primary-fixed: '#001a42'
  on-primary-fixed-variant: '#004395'
  secondary-fixed: '#acedff'
  secondary-fixed-dim: '#4cd7f6'
  on-secondary-fixed: '#001f26'
  on-secondary-fixed-variant: '#004e5c'
  tertiary-fixed: '#ffdcc6'
  tertiary-fixed-dim: '#ffb786'
  on-tertiary-fixed: '#311400'
  on-tertiary-fixed-variant: '#723600'
  background: '#f9f9ff'
  on-background: '#191b23'
  surface-variant: '#e1e2ec'
typography:
  display-lg:
    fontFamily: Geist
    fontSize: 48px
    fontWeight: '700'
    lineHeight: '1.1'
    letterSpacing: -0.04em
  headline-xl:
    fontFamily: Geist
    fontSize: 32px
    fontWeight: '600'
    lineHeight: '1.2'
    letterSpacing: -0.02em
  headline-lg:
    fontFamily: Geist
    fontSize: 24px
    fontWeight: '600'
    lineHeight: '1.3'
  body-lg:
    fontFamily: Inter
    fontSize: 18px
    fontWeight: '400'
    lineHeight: '1.6'
  body-md:
    fontFamily: Inter
    fontSize: 16px
    fontWeight: '400'
    lineHeight: '1.5'
  body-sm:
    fontFamily: Inter
    fontSize: 14px
    fontWeight: '400'
    lineHeight: '1.5'
  label-md:
    fontFamily: Geist
    fontSize: 12px
    fontWeight: '500'
    lineHeight: '1'
    letterSpacing: 0.05em
  mono-code:
    fontFamily: Geist
    fontSize: 14px
    fontWeight: '400'
    lineHeight: '1.6'
rounded:
  sm: 0.25rem
  DEFAULT: 0.5rem
  md: 0.75rem
  lg: 1rem
  xl: 1.5rem
  full: 9999px
spacing:
  unit: 4px
  container-margin: 24px
  gutter: 16px
  section-gap: 40px
  card-padding: 20px
---

## 品牌与风格 (Brand & Style)

本设计系统旨在定义一种**前瞻性、高性能且极致精准**的视觉语言。其核心理念是将 Apple 式的极简主义与 Vercel/Linear 的开发者美学相结合，专为复杂的 AI 基础设施管理而打造。

### 核心视觉支柱：
*   **极致简约 (Apple-like Minimalism):** 大面积的留白与精准的对齐，消除视觉噪音，让用户专注于数据决策。
*   **磨砂玻璃质感 (Glassmorphism):** 通过半透明层级、背景模糊（Backdrop Blur）与微弱的内发光边缘，营造出轻盈的浮动感与未来感。
*   **精密感 (Technical Precision):** 结合微小的物理阴影与线性渐变，模拟高端硬件与云端算力的无缝衔接。
*   **动态响应 (Micro-interactions):** 交互应具有高物理感，如磁贴吸附效果、微妙的缩放反馈与流光过渡。

目标是为运维工程师与 AI 架构师提供一个既显专业稳重，又不失创新活力的工作环境，通过高溢价感的视觉呈现提升产品的信任度。

## 色彩系统 (Colors)

色彩配置以纯净的白色为基调，通过高饱和度的科技感渐变点亮核心交互。

*   **基础背景:** 使用纯白 (`#FFFFFF`) 作为容器表面，底色采用超浅灰 (`#F9FAFB`) 以区分层级。
*   **品牌主色:** 采用动态蓝到深蓝的渐变，象征数据流的稳定性。青色 (Cyan) 作为辅助，增强科技氛围。
*   **点缀色:** 紫罗兰色 (Violet) 用于 AI 相关的功能模块或高级洞察，体现智能化；蓝绿色 (Teal) 用于次级强调。
*   **状态语义:**
    *   **Success:** 柔和的翠绿色，用于表示节点运行正常。
    *   **Warning:** 琥珀色，用于资源占用预警。
    *   **Error:** 玫瑰红，用于致命故障或服务器中断告警。
*   **光效应用:** 在悬浮状态下，允许使用极低不透明度的蓝色发光效果 (Glow) 来增强深度感。

## 字体排版 (Typography)

排版系统优先考虑开发者的阅读效率与数据的层次感。

*   **字体选择:** 标题与标签采用 **Geist**，利用其半等宽的特性展现技术精密感；正文采用 **Inter**，确保长篇数据的易读性。针对简体中文，系统应回退至高质量的无衬线黑体（如 PingFang SC）。
*   **层级策略:** 严格区分 `Display`（大屏监控）、`Headline`（模块标题）与 `Body`（具体参数）。
*   **特殊处理:** 对于服务器日志、代码片段和 IP 地址，必须使用 `mono-code` 样式以对齐字符。标签 (Label) 统一使用大写或中等字重，并配合略微加宽的字间距以增强识别度。

## 布局与间距 (Layout & Spacing)

采用 **8px 栅格系统**，但在微调时支持以 4px 为步长，确保组件在各种分辨率下的严丝合缝。

*   **网格模型:** 标准桌面端采用 12 列流式栅格，槽宽 (Gutter) 固定为 16px。在管理后台中，侧边栏通常采用 240px 的固定宽度，主内容区随窗口自由伸缩。
*   **响应式规则:**
    *   **Desktop (1440px+):** 全功能显示，拓扑图展开。
    *   **Tablet (1024px):** 侧边栏折叠为图标模式，数据卡片由每行 4 个变为 2 个。
    *   **Mobile (640px):** 移除不必要的装饰性元素，拓扑图转换为列表视图，全局边距缩减至 16px。
*   **呼吸感:** 模块之间保持较大的 Section Gap (40px+)，通过物理距离而非粗黑线来区分不同功能区。

## 层级与深度 (Elevation & Depth)

本系统不使用强烈的黑色投影，而是通过多层级、带颜色的漫反射阴影来构建空间感。

*   **Z-轴层级:**
    *   **Level 0 (Base):** 背景层 (`#F9FAFB`)。
    *   **Level 1 (Card):** 基础卡片，使用极细的浅灰色边框 (`1px solid #E5E7EB`)，无阴影或带极淡的 2px 扩展阴影。
    *   **Level 2 (Hover/Floating):** 悬浮卡片，触发 15% 浓度的深蓝色漫反射阴影，位移 Y 轴增加，呈现“漂浮”视觉。
    *   **Level 3 (Modals/Overlays):** 使用 20px 背景模糊 (Backdrop Blur) 的磨砂玻璃效果，边缘辅以 0.5px 的半透明白色亮边，模拟玻璃厚度。
*   **深度隐喻:** AI 建议窗口应始终处于最高层级，并伴有微妙的蓝色光晕 (Ambient Glow)，引导用户视觉重心。

## 形状语言 (Shapes)

形状遵循“圆润但不失严谨”的原则。

*   **基础组件:** 按钮与输入框采用 `rounded-md` (0.5rem)，平衡了亲和力与工业感。
*   **容器:** 数据卡片、AI 洞察面板采用 `rounded-lg` (1rem)，在大屏幕上显得优雅。
*   **浮动面板:** 搜索框、全局命令菜单 (Command Palette) 采用 `rounded-xl` (1.5rem)，强调其独立于基础排版的特殊属性。
*   **状态指示器:** 小型的状态点、标签 (Chips) 采用全圆角 (Pill-shaped)，以便于在复杂表格中快速识别。

## 组件系统 (Components)

*   **高级数据表格 (Data Tables):**
    *   表头采用加粗的文本与微弱的底部分隔线。
    *   支持实时流式加载动画，行在悬浮时产生轻微的位移反馈。
    *   集成小型火花图 (Sparklines) 展示近 5 分钟的 CPU/内存趋势。
*   **AI 洞察卡片 (AI Insight Cards):**
    *   背景使用淡紫色渐变与玻璃质感融合。
    *   卡片左上角带有动态流光边缘 (Shimmer Effect)，暗示正在进行后台计算。
*   **服务器拓扑图 (Topology Visualizations):**
    *   节点以带有发光外框的圆角方块表示。
    *   连接线采用 SVG 路径，动态流动的点表示数据传输方向。
*   **时间线节点 (Timeline Nodes):**
    *   用于显示部署历史与故障回溯。
    *   过去事件置灰，当前活跃事件使用 Primary Gradient 发光显示。
*   **输入字段 (Input Fields):**
    *   默认状态为幽灵样式 (Ghost style)，聚焦时边框转化为品牌蓝色渐变，并伴有内阴影。