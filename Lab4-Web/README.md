# Lab4 — Java Web Browser và HTTP Server

Client, server và bộ kiểm thử đều viết bằng **Java**. Không cần Python.

- **Client JavaFX:** nhiều tab WebView độc lập, tìm kiếm Google từ thanh địa chỉ, GET, HEAD, soạn POST, lịch sử lưu trên máy và quản lý tải xuống.
- **HTTP service:** `java.net.http.HttpClient.sendAsync`, timeout, hủy request, tải file theo luồng và giữ file đã tồn tại.
- **Server Java:** `jdk.httpserver`, cung cấp HTML, JSON và upload/download file qua kết nối HTTP thật.

## Chuẩn bị

- **JDK 17+** cho server và các kiểm thử HTTP.
- **JavaFX 21** cho giao diện. Tải thư viện qua Maven Wrapper lần đầu, hoặc đặt `JAVAFX_HOME` trỏ tới JavaFX 21 SDK.
- Đặt `JAVA_HOME` trỏ đến thư mục JDK. Script cũng tìm `javac` trong `PATH`.

Nếu cần tải JavaFX qua Maven:

```powershell
.\mvnw.cmd -q package
```

Các lệnh dưới đây chạy tại thư mục `Lab4-Web`. Nếu PowerShell chặn script trong phiên hiện tại:

```powershell
Set-ExecutionPolicy -Scope Process Bypass
```

## Chạy server

```powershell
.\scripts\run-server.ps1 -Port 8080
```

Server và kiểm thử HTTP dùng thư viện có sẵn trong JDK, không cần JavaFX hoặc tải Maven dependency. Để cho client trong LAN kết nối, dùng `-BindAddress 0.0.0.0` và cho phép TCP 8080 qua firewall.

| Route | Method | Dịch vụ |
|---|---|---|
| `/`, `/index.html` | GET/HEAD | Trang HTML và liên kết demo |
| `/api/time` | GET/HEAD | Thời gian UTC dạng JSON |
| `/api/echo?message=hello` | GET/HEAD | Echo query dạng JSON |
| `/api/echo` | POST | Nhận JSON, form hoặc plain text; trả lại payload và header |
| `/api/upload?name=demo.bin` | POST | Lưu bytes, tối đa 2 MiB; trả đường dẫn tải |
| `/download/<name>` | GET/HEAD | Tải file từ thư mục upload hoặc `public` |
| `/files/sample.pdf`, `/files/sample.mp3`, `/files/sample.mp4` | GET/HEAD | Mở các tệp mẫu, hỗ trợ yêu cầu lấy một đoạn bằng `Range` |

Trang chủ có sẵn PDF, MP3 và MP4 mẫu. Các nút **Download PDF**, **Download MP3** và **Download MP4** tải tệp trực tiếp. Server trả đúng Content-Type và Content-Length theo loại tệp. HEAD không có body. Mức hỗ trợ xem hoặc phát tại chỗ tùy WebView và codec của máy.

## Chạy browser

Mở terminal khác:

```powershell
.\scripts\run-client.ps1 -HomeUrl http://127.0.0.1:8080/
```

1. Nhấn Ctrl+L rồi nhập từ khóa như `lập trình mạng Java` và nhấn Enter để xem kết quả Google ngay trong tab đang mở. Có thể nhập URL hoặc tên miền như `wikipedia.org` để mở trang trực tiếp. Tên miền thường dùng HTTPS, còn địa chỉ IP, `localhost` và địa chỉ có cổng dùng HTTP nếu bạn chưa ghi scheme. **GET** gửi yêu cầu đến địa chỉ đã nhập. Tìm kiếm Google cần kết nối Internet.
2. **HEAD:** xem status và header thật trong tab **HTTP response**.
3. **POST:** mở cửa sổ soạn request. Nhập URL, header theo dạng `Tên: giá trị` mỗi dòng và nội dung body. Ví dụ:

   ```text
   Content-Type: application/json; charset=UTF-8
   X-Lab-Test: demo
   ```

   ```json
   {"message":"Xin chào","lab":4}
   ```

   Bấm **Send POST** hoặc Ctrl+Enter. Đổi Content-Type để gửi `text/plain` hoặc `application/x-www-form-urlencoded`. Body được gửi theo nội dung bạn nhập, không dùng payload cố định. Các header do HTTP client quản lý như Host/Content-Length không được nhập thủ công.
4. Tab **HTTP response** trình bày method, status, thời gian, URL, header và body bằng điều khiển JavaFX. Nội dung response không được ghép thành HTML để thực thi.
5. **Reload** (F5) tải lại trang đang mở. **Stop** (Esc) ngắt tải trang. Dùng **Cancel request** để hủy lệnh HEAD hoặc POST.
6. Bấm liên kết tải xuống trên trang demo để lưu vào thư mục `Downloads` của tài khoản Windows. Cửa sổ **Downloads** hiển thị tiến độ, cho phép hủy hoặc mở thư mục chứa tệp đã tải. **Save URL** dùng địa chỉ trên thanh URL và cho chọn thư mục lưu. Tên trùng được đổi để giữ tệp đã có.
7. Dùng **New tab** hoặc Ctrl+T để mở tab duyệt. Mỗi tab giữ trang, Back/Forward và zoom riêng. Ctrl+W đóng tab đang chọn, Ctrl+L đưa tiêu điểm đến thanh địa chỉ.
8. **History** hiển thị tối đa 500 lượt mở trang HTTP/HTTPS và lưu trong `%USERPROFILE%\.ronial-browser\history.tsv`. Bấm đúp hoặc chọn **Mở trang** để mở lại. Có thể xóa lịch sử từ chính cửa sổ này.

## Kiến trúc

```text
src/main/java/com/ronial/browser/
  ClientController.java      Nối sự kiện và điều hướng WebView
  view/                      RequestComposer, ResponsePane
  viewmodel/                 BrowserViewModel: trạng thái, hủy và điều phối tác vụ
  model/                     HttpRequestData, HttpResponseData, DownloadResult
  service/                   AddressBar, BrowsingHistory, HttpService
  server/                    LabHttpServer, ServerMain, JSON codec nội bộ
src/main/resources/          FXML và CSS
tests/                       Kiểm thử HTTP/service/JavaFX bằng Java
scripts/                     Build, chạy server/client và kiểm thử
```

Controller không gửi HTTP, ghi file download hoặc dựng chuỗi HTML response. Model/service không phụ thuộc JavaFX. Server có thể chạy riêng trên máy khác; không cần database theo phạm vi Lab4 hiện tại.

## Kiểm thử

```powershell
.\test.ps1                  # Server + HTTP service; JDK 17+ là đủ
.\test.ps1 -Ui              # Thêm giao diện JavaFX thực
.\scripts\test-features.ps1 # Tương đương kiểm tra có UI
```

`test.cmd` nhận cùng tham số và trả mã lỗi khi kiểm thử thất bại. Không bỏ qua lỗi kết nối hay báo hoàn tất khi Java trả lỗi. Dữ liệu thử nằm dưới `target`, tách khỏi dữ liệu demo.

Xem [phạm vi kiểm thử](KIEM-THU.md). Đây là browser phục vụ bài lab dựa trên JavaFX WebView; không cam kết tương thích mọi tính năng của website dùng trình duyệt mới nhất.
