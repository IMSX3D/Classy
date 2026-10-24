# Security Policy / 安全策略

## Supported Versions / 支持的版本

只有最新发布的版本接收安全修复。请先升级到最新版再报告。

Only the latest release receives security fixes. Please upgrade to the latest version before reporting.

## Reporting a Vulnerability / 报告漏洞

**不要用公开 issue 报告安全漏洞。**

- 优先使用 GitHub 的[私密漏洞报告](https://github.com/IMSX3D/Classy/security/advisories/new)
- 也可以通过开发者 QQ `547991704` 私下联系（个人项目，不保证实时回复）

请在报告中包含：影响的版本、复现步骤、影响评估。收到后会确认，但个人项目不承诺固定的回复时限。

Do not report security vulnerabilities through public issues. Use GitHub private
vulnerability reporting (link above), or contact the maintainer privately on QQ
`547991704`. Include the affected version, reproduction steps, and your impact
assessment. Reports are acknowledged when read; as a personal project, no fixed
response time is promised.

## Scope / 范围

Classy 是本地课表应用，以下方面尤其值得关注：

- 导入解析路径的文件处理（外部打开 JSON/HTML/日历/CSV/XML 等 10 种 MIME、分享接收）
- WebView 教务直连的 JS 注入与凭据处理（Cookie 不出设备）
- 导出文件中的个人信息泄漏

Classy is a local timetable app. Areas of particular interest:

- File handling along the import/parse paths (10 MIME types opened externally — JSON, HTML, calendar, CSV, XML — plus share intents)
- JS injection and credential handling in the WebView academic-system connector (cookies never leave the device)
- Personal information leaking through exported files
