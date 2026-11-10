# Kiểm thử bản Lab4 Java

Ngày kiểm tra: **10/11/2026**. Client, server và bộ kiểm thử chạy bằng Java, không gọi Python.

## Các bộ kiểm tra

| Bộ kiểm tra | Nội dung |
|---|---|
| `ServerIntegrationTest` | Kết nối HTTP thật; HTML, JSON time/echo, GET/HEAD/POST, Unicode, header tùy nhập, form/text/JSON, JSON sai, upload/download nhị phân khớp SHA-256, giới hạn upload, chặn đường dẫn ngoài thư mục, 404/405 |
| `HttpServiceIntegrationTest` | `HttpClient.sendAsync`, body/header thực, HEAD, lỗi HTTP, tên file Unicode, tải song song cùng tên, giữ file đã có, hủy tải và dọn file tạm |
| `BrowserFunctionalTest` | 18 kiểm tra trên JavaFX thực: GET, back/forward/history, HEAD, soạn POST JSON/form/text và header tùy nhập, giữ draft khi nhập sai, response native, lỗi 400 và phục hồi, chống gửi lặp, hủy request, kết quả cũ không ghi đè trang mới, download và zoom |

## Chạy lại

```powershell
.\test.ps1          # Server + HTTP service; chỉ cần JDK 17+
.\test.ps1 -Ui      # Thêm JavaFX; cần JavaFX 21
```

Mỗi bộ kiểm thử dùng localhost và thư mục riêng dưới `target/test-runtime`. Assertion, lỗi biên dịch hay lỗi kết nối làm script thất bại; không chuyển thành kết quả PASS/SKIP.

Ảnh chụp từ scene thật được tạo tại `target/browser-response.png` và `target/request-composer.png`. Kết quả build, ảnh và dữ liệu thử được loại khỏi Git.

Đây là kiểm thử cục bộ trên Windows. Chưa thay cho việc chạy thử mạng LAN, proxy hay mọi website công cộng. JavaFX WebView là bộ máy duyệt trang của ứng dụng; bài lab không triển khai toàn bộ chức năng của Chrome.
