# 📱 Phân Tích Dự Án: Hisense E-Ink Auto Refresh

## 1. Tổng Quan Dự Án

### Mục Đích
Công cụ làm mới toàn màn hình tự động cho thiết bị E-Ink Hisense. Ứng dụng giải quyết hiện tượng "ghosting" (mấu nhoè) trên màn hình e-ink bằng cách:
- Theo dõi số lượng thao tác đã thực hiện (chạm/phím)
- Tự động làm mới toàn bộ màn hình khi đạt ngưỡng cấu hình
- Phát hiện tự động các màn hình đọc sách để tránh làm mới không cần thiết

### Thông Tin Cơ Bản
| Thông Số | Giá Trị |
|---------|--------|
| **Package Name** | `com.liziwa.hisense_autorefresh` |
| **Phiên Bản Hiện Tại** | 1.3.2 (versionCode: 6) |
| **Target SDK** | 30 |
| **Min SDK** | 28 (Android 9+) |
| **Compile SDK** | 36 |
| **Ngôn Ngữ** | Kotlin + XML |
| **Build Tool** | Gradle (AGP 9.4.0) |

---

## 2. Kiến Trúc Dự Án

### 2.1 Cấu Trúc Thư Mục
```
app/
├── src/main/
│   ├── java/com/liziwa/hisense_autorefresh/
│   │   ├── activity/
│   │   │   ├── MainActivity.kt          # Giao diện chính
│   │   │   └── AppsActivity.kt          # Quản lý danh sách ứng dụng
│   │   ├── util/
│   │   │   ├── PermissionHelper.kt      # Xử lý quyền
│   │   │   ├── NotificationUtils.kt     # Thông báo
│   │   │   ├── Utils.kt                 # Tiện ích chung
│   │   │   ├── DateFileNameGenerator.kt # Quản lý tên file
│   │   │   └── SingletonHolder.kt       # Pattern Singleton
│   │   ├── view/                        # Custom UI components
│   │   ├── AppPreferences.kt            # Quản lý cấu hình (SharedPreferences)
│   │   ├── BootReceiver.kt              # Khởi động khi bật thiết bị
│   │   ├── EInkAccessibilityService.kt  # Core service (theo dõi hoạt động)
│   │   └── MyApp.kt                     # Application class
│   ├── res/                             # Resources
│   └── AndroidManifest.xml
└── build.gradle.kts
```

### 2.2 Thành Phần Chính (Components)

#### **1️⃣ EInkAccessibilityService** (Core Logic)
- **Loại**: `AccessibilityService`
- **Vai Trò**: Thành phần chính theo dõi và điều khiển toàn bộ chức năng
- **Chức Năng**:
  - Theo dõi các sự kiện thay đổi cửa sổ (`TYPE_WINDOW_STATE_CHANGED`)
  - Theo dõi các thao tác người dùng (`TYPE_VIEW_CLICKED`)
  - Phân tích nội dung màn hình để xác định "reading screen" (màn hình đọc sách)
  - Quản lý bộ đếm thao tác và kích hoạt làm mới
  - Xử lý làm mới định kỳ theo thời gian
  - Gọi Hisense EPD Interface để thực hiện làm mới màn hình

#### **2️⃣ MainActivity** (UI Chính)
- **Loại**: `AppCompatActivity`
- **Vai Trò**: Giao diện chính cho người dùng
- **Chức Năng**:
  - Hiển thị trạng thái dịch vụ
  - Cấu hình các tham số: ngưỡng làm mới, khoảng thời gian bỏ qua, v.v.
  - Nút "Test Refresh" để kiểm tra chức năng
  - Bật/tắt chế độ gỡ lỗi (Debug Mode) bằng cách nhấp 10 lần vào tiêu đề
  - Khởi động dịch vụ Accessibility
  - Hot-update cấu hình qua broadcast

#### **3️⃣ AppsActivity** (Quản Lý Ứng Dụng)
- **Loại**: `AppCompatActivity`
- **Vai Trò**: Quản lý danh sách ứng dụng được giám sát
- **Chức Năng**:
  - Hiển thị danh sách ứng dụng đã cài đặt
  - Cho phép chọn ứng dụng cần giám sát
  - Danh sách trắng (Ignored Apps/Whitelist)
  - Cấu hình tham số riêng biệt cho từng ứng dụng

#### **4️⃣ BootReceiver** (Khởi Động Tự Động)
- **Loại**: `BroadcastReceiver`
- **Vai Trò**: Tự động khởi động dịch vụ khi bật thiết bị
- **Action**: `android.intent.action.BOOT_COMPLETED`

#### **5️⃣ AppPreferences** (Quản Lý Cấu Hình)
- **Loại**: Singleton
- **Vai Trò**: Lưu trữ và quản lý tất cả cấu hình
- **Storage**: `SharedPreferences`
- **Hot Update**: Gửi broadcast `ACTION_CONFIG_CHANGE` khi cấu hình thay đổi

---

## 3. Luồng Dữ Liệu & Quy Trình Làm Việc

### 3.1 Luồng Chính - Đếm Thao Tác & Làm Mới
```
Người dùng Tương Tác
    ↓
AccessibilityService nhận sự kiện
    ↓
Kiểm tra ứng dụng đang chạy
    ↓
Tìm nạp thông tin cấu hình từ AppPreferences
    ↓
[Quyết Định Thực Hiện Khi Nào]
├─ Lần đầu chuyển màn hình → Phân tích nội dung
├─ Reading Screen? → Cộng bộ đếm
├─ Đạt ngưỡng? → Làm mới (gọi EpdManager.forceClear())
└─ Không phải? → Bỏ qua hoặc tái phát hiện sau 5 lần
```

### 3.2 Phát Hiện Màn Hình Đọc (Reading Screen Detection)
```
Khi chuyển màn hình:
    ↓
[Kiểm Tra Ưu Tiên]
├─ Trong Whitelist? → Coi là reading screen ✅ (Ưu tiên cao nhất)
├─ System App? → Bỏ qua phân tích ❌
└─ Ứng dụng thường → Phân tích nội dung

[Phân Tích Nội Dung]
    Duyệt các node trong cửa sổ
    ↓
    Đếm ký tự văn bản (chỉ visible text)
    ↓
    Tính tỉ lệ: TextLength / NodeCount
    ↓
    Nếu (Text > 150 ký tự) AND (Tỉ lệ > 10)
        → Reading Screen ✅
    Ngược lại
        → Non-reading Screen ❌
```

### 3.3 Làm Mới Định Kỳ (Repeatedly Refresh)
```
Handler Timer (Mỗi X giây)
    ↓
Màn hình đã mở? → Kiểm tra
    ↓
Từ lần làm mới cuối >= 30 giây? → Tránh trùng lặp
    ↓
Gọi EpdManager.forceClear()
    ↓
Reset bộ đếm
```

---

## 4. Dependencies & Thư Viện

### AndroidX & Framework
| Thư Viện | Phiên Bản | Mục Đích |
|---------|----------|---------|
| `androidx.core:core-ktx` | 1.17.0 | Core Android extensions |
| `androidx.appcompat:appcompat` | 1.7.1 | Backward compatibility |
| `androidx.activity:activity` | 1.10.1 | Activity base class |
| `androidx.constraintlayout` | 2.2.1 | Layout engine |
| `androidx.recyclerview` | 1.4.0 | List/grid UI |
| `androidx.lifecycle:lifecycle-runtime-ktx` | 2.9.3 | Lifecycle management |

### Utilities
| Thư Viện | Phiên Bản | Mục Đích |
|---------|----------|---------|
| `kotlinx.coroutines:coroutines-android` | 1.10.2 | Async programming |
| `com.elvishew:xlog` | 1.11.1 | **Logging (Debug mode)** |
| `com.google.android.material:material` | 1.12.0 | Material Design UI |

### Build Tool
- **AGP (Android Gradle Plugin)**: 9.4.0
- **Kotlin**: 2.4.20

---

## 5. Quyền Yêu Cầu (Permissions)

### Quyền Bắt Buộc
| Quyền | Mục Đích |
|------|---------|
| `BIND_ACCESSIBILITY_SERVICE` | Core: Theo dõi sự kiện màn hình & thao tác |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Ngăn quy trình bị dừng bởi tiết kiệm pin |
| `RECEIVE_BOOT_COMPLETED` | Khởi động tự động sau khi bật thiết bị |
| `QUERY_ALL_PACKAGES` | Liệt kê các ứng dụng đã cài (Android 11+) |

### Quyền Tùy Chọn
| Quyền | Mục Đích |
|------|---------|
| `SYSTEM_ALERT_WINDOW` | Overlay theo dõi chạm (nếu bật) |
| `POST_NOTIFICATIONS` | Thông báo dịch vụ foreground |
| `VIBRATE` | Phản hồi cơ học |

---

## 6. Cấu Hình & Tính Năng

### 6.1 Cấu Hình Chính
```
┌─ Monitor All Applications (Giám sát tất cả)
├─ Respond to Touch / Key (Đếm thao tác chạm/phím)
├─ Refresh Interval (Ngưỡng làm mới: ví dụ 10 lần)
├─ Ignore Time (Khoảng thời gian tối thiểu: ms)
├─ Auto-detect Reading Screens (Tự động phát hiện màn hình đọc)
├─ Repeatedly Refresh (Làm mới định kỳ: giây, 0 = tắt)
├─ Debug Mode (Bật bằng cách nhấp 10 lần tiêu đề)
├─ Ignored Applications (Danh sách trắng)
└─ Monitored Applications (Ứng dụng được giám sát)
```

### 6.2 Đặc Biệt - Debug Mode
- **Kích Hoạt**: Nhấp 10 lần vào tiêu đề chính
- **Hiệu Ứng**: 
  - XLog ghi log vào file (thư mục `Android/data/<package>/files/xlog/`)
  - Bao gồm thread info, call stacks
  - Lưu giữ 7 ngày (lưu trữ hàng ngày)
- **Vô Hiệu Hóa**: Nhấp 10 lần lại để tắt

---

## 7. Giao Diện E-Ink Hisense

### 7.1 Phụ Thuộc Vào Hisense EPD Manager
```kotlin
// Gọi qua Reflection
com.hmct.epd.EpdManager.forceClear()
```

### 7.2 Lưu Ý
- ⚠️ Interface này là **private API của Hisense**
- Chỉ khả dụng trên thiết bị Hisense hoặc phiên bản hệ thống cụ thể
- Nếu không khả dụng: Lỗi được ghi lại, các chức năng khác vẫn hoạt động bình thường
- Một số phiên bản có thể yêu cầu **tắt Hidden API Restrictions**:
  ```bash
  adb shell settings put global hidden_api_policy 0
  adb shell settings get global hidden_api_policy  # Kết quả: 0
  ```

---

## 8. Hot Update & Broadcast

### 8.1 Cơ Chế Hot Update
Khi người dùng thay đổi cấu hình:

```
MainActivity/AppsActivity
    ↓
Lưu vào AppPreferences (SharedPreferences)
    ↓
Gửi Broadcast: ACTION_CONFIG_CHANGE
    ↓
EInkAccessibilityService nhận broadcast
    ↓
Reload cấu hình ngay lập tức (không cần restart)
```

### 8.2 Lợi Ích
- ✅ Không cần khởi động lại dịch vụ
- ✅ Cấu hình được áp dụng ngay lập tức
- ✅ Trải nghiệm người dùng mượt mà

---

## 9. Tệp Android Manifest

### 9.1 Cấu Hình Ứng Dụng
```xml
<application
    android:name=".MyApp"                          <!-- Application class -->
    android:icon="@mipmap/icon"
    android:label="@string/app_name"
    android:theme="@style/AppTheme"
    android:supportsRtl="false">                    <!-- Không hỗ trợ RTL -->
```

### 9.2 Activities
- **MainActivity** (LAUNCHER) - Giao diện chính
- **AppsActivity** - Quản lý ứng dụng

### 9.3 Services
- **EInkAccessibilityService** - Dịch vụ truy cập (Core)

### 9.4 Receivers
- **BootReceiver** - Khởi động tự động

### 9.5 Độc Quyền Package Queries (Android 11+)
```xml
<queries>
    <intent>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LAUNCHER" />
    </intent>
</queries>
```

---

## 10. Build & Release

### 10.1 Build Configuration
```kotlin
// Signing Config (Release)
signingConfigs {
    create("release") {
        storeFile = file("keystore.jks")
        storePassword = "liziwa"
        keyAlias = "android"
        keyPassword = "liziwa"
    }
}

// Release Build
buildTypes.release {
    signingConfig = signingConfigs["release"]
    minifyEnabled = true        // ProGuard code obfuscation
    shrinkResources = true      // Resource shrinking
    proguardFiles(...)
}
```

### 10.2 Compile Options
- **Source/Target**: Java 11
- **Compile SDK**: 36
- **Target SDK**: 30

### 10.3 Features
- **Data Binding**: Enabled (MVVM pattern)
- **Kotlin**: No longer requires separate kotlin-android plugin (AGP 9.0+)

### 10.4 Localization
```kotlin
androidResources {
    localeFilters += listOf("en", "vi", "zh-rHK", "zh-rTW")
}
```
Hỗ trợ: Tiếng Anh, Tiếng Việt, Tiếng Trung (Hong Kong, Taiwan)

---

## 11. Quy Trình Cài Đặt & Kiểm Tra

### Bước 1: Chuẩn Bị Thiết Bị
```bash
# Kết nối ADB
adb devices

# Tắt Hidden API Restrictions (QUAN TRỌNG)
adb shell settings put global hidden_api_policy 0
adb shell settings get global hidden_api_policy  # Kết quả phải là: 0
```

### Bước 2: Cài Đặt Ứng Dụng
```bash
# Build APK hoặc cài từ release
adb install -r app-release.apk
```

### Bước 3: Cấp Quyền

Cấp quyền thông qua Cài đặt:
- **Accessibility Service**: Settings → Accessibility → [App Name]
- **Overlay Window**: Settings → Apps → [App Name] → Overlay
- **Other Permissions**: Tiếp nạp theo yêu cầu

### Bước 4: Kiểm Tra
- Mở ứng dụng
- Nhấp **"Test Refresh"**
- Màn hình E-Ink sẽ làm mới toàn bộ (nên thấy hiệu ứng rõ ràng)
- Nếu thành công: Tất cả chức năng đã sẵn sàng

---

## 12. Cấu Trúc Dữ Liệu & SharedPreferences

### 12.1 Ví Dụ Key Cấu Hình
```
"monitor_all_apps"              → Boolean (mặc định: true)
"respond_to_touch"              → Boolean (mặc định: true)
"respond_to_key"                → Boolean (mặc định: true)
"refresh_interval"              → Int (mặc định: 10)
"ignore_time_ms"                → Int (mặc định: 500ms)
"auto_detect_reading_screen"    → Boolean (mặc định: true)
"repeatedly_refresh_seconds"      → Int (mặc định: 300s)
"is_debug_mode"                 → Boolean (mặc định: false)
"ignored_apps"                  → String (JSON list)
"monitored_apps"                → String (JSON list)
```

### 12.2 App-specific Config
Mỗi ứng dụng có thể có cấu hình riêng:
```
"app_${packageName}_refresh_count" → Int
"app_${packageName}_delay_ms"      → Int
```

---

## 13. Điểm Mạnh & Yếu Điểm

### ✅ Điểm Mạnh
1. **Tích Hợp Sâu**: Sử dụng AccessibilityService để giám sát chính xác
2. **Thông Minh**: Phát hiện tự động màn hình đọc (text analysis)
3. **Linh Hoạt**: Cấu hình chi tiết cho từng ứng dụng
4. **Hot Update**: Cấu hình được áp dụng ngay mà không cần restart
5. **Debug Mode**: Hỗ trợ gỡ lỗi chi tiết qua log file
6. **Auto Start**: Khởi động tự động sau khi bật thiết bị
7. **Localization**: Hỗ trợ đa ngôn ngữ

### ⚠️ Yếu Điểm & Lưu Ý
1. **API Riêng Tư Hisense**: Phụ thuộc vào `com.hmct.epd.EpdManager` (không công khai)
2. **Chỉ Hoạt Động Trên Hisense**: Không khả dụng trên các thiết bị khác
3. **Hidden API**: Cần tắt Hidden API Restrictions
4. **Performance**: Screen analysis có thể ảnh hưởng nếu nội dung phức tạp
5. **WebView/Canvas**: Không thể phát hiện nội dung canvas hoặc WebView

---

## 14. Tài Nguyên Liên Quan

### 14.1 Các Tệp Quan Trọng
- `EInkAccessibilityService.kt` - Logic chính
- `AppPreferences.kt` - Quản lý cấu hình
- `MainActivity.kt` - UI chính
- `AndroidManifest.xml` - Khai báo ứng dụng
- `app/build.gradle.kts` - Cấu hình build

### 14.2 Thư Mục Log
```
Android/data/com.liziwa.hisense_autorefresh/files/xlog/
├── xlog.YYYY-MM-DD.log    (Log file hàng ngày)
└── ... (tối đa 7 ngày)
```

### 14.3 Gỡ Lỗi
- **Logcat**: `adb logcat | grep hisense_autorefresh`
- **Debug Mode**: Vào ứng dụng và nhấp tiêu đề 10 lần
- **Log File**: Kiểm tra thư mục `xlog/` khi Debug Mode bật

---

## 15. Kết Luận

**Hisense E-Ink Auto Refresh** là một ứng dụng Android chuyên dụng, được thiết kế tối ưu cho các thiết bị e-ink Hisense. Nó kết hợp:
- **AccessibilityService** để giám sát hoạt động người dùng
- **Text Analysis** để xác định loại nội dung
- **Reflection API** để gọi Hisense EPD Manager
- **SharedPreferences** để quản lý cấu hình
- **Hot Update Broadcasting** để cập nhật cấu hình ngay lập tức

Ứng dụng cung cấp kinh nghiệm đọc sách mượt mà trên các thiết bị e-ink Hisense bằng cách tự động quản lý làm mới màn hình theo thao tác và nội dung đọc.

---

*Phân tích được tạo ngày: 10/09/2026*
