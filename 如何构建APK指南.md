# 如何构建Android抓包工具APK

本指南提供了三种方法来构建Android抓包工具的APK文件。

## 方法一：使用Android Studio（推荐）

Android Studio是官方Android开发IDE，提供了最简单的方式来构建APK。

### 步骤：

1. **安装Android Studio**
   - 从[Android Studio官网](https://developer.android.com/studio)下载并安装Android Studio
   - 安装过程中，确保选择安装Android SDK

2. **打开项目**
   - 启动Android Studio
   - 选择"Open an Existing Project"
   - 导航到项目目录（E:\cursor\简单android抓包工具）并打开

3. **同步项目**
   - Android Studio会自动检测Gradle文件并提示同步项目
   - 点击"Sync Now"按钮，等待同步完成
   - 如果遇到任何错误，按照提示解决

4. **构建APK**
   - 点击顶部菜单的"Build"
   - 选择"Build Bundle(s) / APK(s)"
   - 选择"Build APK(s)"
   - 等待构建完成

5. **查找APK文件**
   - 构建完成后，Android Studio会显示通知
   - 点击通知中的"locate"链接，或者直接导航到以下路径：
   - `app/build/outputs/apk/debug/app-debug.apk`

## 方法二：使用命令行（需要Java JDK）

如果你更喜欢使用命令行，可以按照以下步骤操作：

### 步骤：

1. **安装Java JDK**
   - 从[Oracle官网](https://www.oracle.com/java/technologies/downloads/)或[OpenJDK](https://adoptium.net/)下载JDK 11或更高版本
   - 安装JDK并设置JAVA_HOME环境变量

2. **设置JAVA_HOME环境变量**
   - 右键点击"此电脑"，选择"属性"
   - 点击"高级系统设置"
   - 点击"环境变量"
   - 在"系统变量"部分，点击"新建"
   - 变量名输入：`JAVA_HOME`
   - 变量值输入JDK安装路径，例如：`C:\Program Files\Java\jdk-11.0.12`
   - 点击"确定"保存
   - 编辑"Path"变量，添加：`%JAVA_HOME%\bin`
   - 重启命令提示符或PowerShell

3. **运行Gradle构建**
   - 打开命令提示符或PowerShell
   - 导航到项目目录：`cd E:\cursor\简单android抓包工具`
   - 运行构建命令：`.\gradlew.bat assembleDebug`
   - 等待构建完成

4. **查找APK文件**
   - 构建完成后，APK文件将位于：
   - `app\build\outputs\apk\debug\app-debug.apk`

## 方法三：使用在线构建服务

如果你不想在本地安装开发工具，可以使用在线构建服务：

### 步骤：

1. **压缩项目文件**
   - 将整个项目目录压缩成ZIP文件

2. **使用在线构建服务**
   - 访问[AppCircle](https://appcircle.io/)、[Bitrise](https://www.bitrise.io/)或[Codemagic](https://codemagic.io/)等在线构建服务
   - 注册账号
   - 上传ZIP文件
   - 按照服务提供的指南配置构建
   - 启动构建过程

3. **下载APK**
   - 构建完成后，从服务提供的链接下载APK文件

## 安装APK到Android设备

无论使用哪种方法构建APK，安装步骤都是相同的：

1. **准备Android设备**
   - 在Android设备上，进入"设置" > "安全"或"隐私"
   - 启用"未知来源"或"安装未知应用"选项

2. **传输APK文件**
   - 使用USB数据线将设备连接到电脑
   - 将APK文件复制到设备存储
   - 或者通过电子邮件、云存储等方式传输APK文件

3. **安装APK**
   - 在Android设备上，使用文件管理器找到APK文件
   - 点击APK文件，按照提示完成安装

## 使用应用

安装完成后，按照以下步骤使用抓包工具：

1. 打开应用
2. 点击"选择配置"按钮，选择JSON格式的重写规则配置文件
3. 点击"开始抓包"按钮，授予VPN权限
4. 应用将开始捕获网络流量，并根据配置文件重写匹配的请求
5. 点击"停止抓包"按钮停止服务
6. 可以使用"导出日志"按钮将捕获的数据包导出为CSV文件 