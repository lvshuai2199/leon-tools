# 注册码 HMAC-SHA256 规则说明

## 1. 适用范围

本文档说明 `charge-tool` 与 Vue3 注册码生成服务之间的 SHA-256 注册码计算规则。

适用组件：

- `personal-server/LeonPro_frontend/charge-tool`
- `personal-server/LeonPro_frontend/vue3_frontend`
- `personal-server/LeonPro_backend/SpringBoot`

## 2. SHA-256 计算规则

SHA-256 配置不是将后缀直接拼接到注册码后再计算普通 SHA-256，而是使用 HMAC-SHA256：

```text
HMAC-SHA256(
    message = 输入的注册码,
    key     = 配置中的加密字符后缀
)
```

当前友博配置使用：

```text
加密方式：SHA-256
加密字符后缀：youbo_leon
```

计算结果输出为小写十六进制字符串，共 64 位。

## 3. 示例

输入注册码：

```text
123456
```

HMAC 密钥：

```text
youbo_leon
```

完整计算结果：

```text
51ee2e66fa749fbece76fb931151ced16fd3a028e0ef32ae1833f69f91bf62fd
```

`charge-tool` 会使用该结果的前缀进行校验。注册码的有效期部分由密文长度区分，例如前 6 位对应一个月，前 7 位对应两个月，前 8 位对应四个月，前 9 位对应六个月，前 10 位对应十三个月，前 12 位对应永久有效。

## 4. 配置要求

在注册码配置页面中，友博相关配置应设置为：

| 配置项 | 值 |
| --- | --- |
| 加密方式 | `SHA-256` |
| 加密字符后缀 | `youbo_leon` |

注意：`encryptSuffix` 在 SHA-256 模式下是 HMAC 密钥，不是普通字符串拼接后缀。

## 5. 代码修改位置

### 后端

`personal-server/LeonPro_backend/SpringBoot/src/main/java/springboot/utils/HashUtil.java`

- 新增 `hmacSha256(String input, String secretKey)`。
- 使用 Java `HmacSHA256` 算法。
- 使用 UTF-8 编码。
- 输出小写十六进制字符串。

`personal-server/LeonPro_backend/SpringBoot/src/main/java/springboot/controller/web/SysGeneralController.java`

- 当配置为 `SHA-256` 或 `SHA256` 时，调用 HMAC-SHA256。
- MD5 和其他旧算法继续使用原有的“注册码 + 后缀”逻辑。

### Vue3 前端

`personal-server/LeonPro_frontend/vue3_frontend/src/views/tool/regcode-config/index.vue`

- 新增配置时默认加密方式为 `SHA-256`。
- 新增配置时默认密钥为 `youbo_leon`。
- 更新了配置项提示，明确该字段在 SHA-256 模式下作为 HMAC 密钥使用。

## 6. 验证结果

Vue3 前端执行以下命令已通过：

```bash
npm run build
```

后端代码逻辑已完成修改。若 Maven 编译出现 `mybatis-spring` class 文件版本错误，需要使用项目要求的 JDK 21 后重新编译：

```bash
mvn -DskipTests compile
```

该问题属于本机 Java 环境版本不匹配，不是本次 HMAC-SHA256 代码错误。

