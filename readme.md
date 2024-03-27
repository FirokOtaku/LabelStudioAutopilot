# Label Studio Autopilot

[English readme](readme-en.md)

使用 Detectron2 为 Label Studio 项目自动创建图像实例分割标注.

![example](example.png)

基于木兰宽松许可证 (第2版) 协议开源.

## 运行环境

* Java 21
  * 为什么用 Java? 因为写 Python 会导致生理不适.
* [我自己 fork 和修改过的 Detectron2](https://github.com/FirokOtaku/Detectron2)
  * 区别是比官方分支多了几个工具脚本, 方便在语言和框架之间做数据交互
* [Label Studio](https://github.com/HumanSignal/label-studio/)
  * 当前项目在 Label Studio 1.10.1 dev 版本通过测试,  
    其它版本 Label Studio 应当也能使用

## 使用

1. 编译本项目, 并准备好上述环境
2. 提供一个 JSON5 配置文件 ([示例配置文件](config-template.json5)),
   置于 `./config.json5` 路径
3. 启动运行

> 因为这个项目用到几个我自己写的 Java 库,  
> 这些项目发布在 GitHub Maven 仓库.
> 你可能需要对 Maven 进行一定配置,  
> 才能正常访问 GitHub Maven 仓库拉取依赖
> 
> * [Label Studio Connector Java](https://github.com/FirokOtaku/LabelStudioConnectorJava)
> * [Topaz](https://github.com/FirokOtaku/Topaz)
> * [Alloy Wrench](https://github.com/FirokOtaku/AlloyWrench)
