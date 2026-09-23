# 中文字体许可

本版使用 Android Open Source Project 的 DroidSansFallback.ttf 子集，文件位于 assets/fonts/recipe-zh.ttf。通过 tools/subset-font.py 只保留界面使用的字符，字体以未压缩 asset 打包。

- 来源：https://github.com/aosp-mirror/platform_frameworks_base/blob/gingerbread/data/fonts/DroidSansFallback.ttf
- 原始声明：[DroidSansFallback-NOTICE.txt](DroidSansFallback-NOTICE.txt)
- 许可证：[Apache License 2.0](Apache-2.0.txt)

字体加载失败时回退相机系统字体，不重新抛出加载异常。实际中文显示仍需在相机上确认。应用本身继续遵循仓库 MIT 许可证。
