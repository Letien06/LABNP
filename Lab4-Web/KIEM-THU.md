# Kiểm thử bản Lab4 Java

Ngày kiểm tra **15/11/2026**, trên Windows với JDK 17 và JavaFX 21.

## Các bộ kiểm tra

| Bộ kiểm tra | Nội dung |
|---|---|
| `ServerIntegrationTest` | HTTP thật, GET/HEAD/POST, HTML, JSON, form, Unicode, upload/download nhị phân, MIME và Range của tệp mẫu, giới hạn upload và xử lý lỗi |
| `HttpServiceIntegrationTest` | HTTP bất đồng bộ, tên tệp Unicode, tải song song, tiến độ, giữ tệp đã có, hủy tải và dọn tệp tạm |
| `BrowsingHistoryTest` | Đọc lại lịch sử từ tệp, giữ thứ tự, bỏ qua dòng hỏng và duy trì trạng thái sau khi xóa |
| `BrowserFunctionalTest` | 30 kiểm tra JavaFX, gồm Reload đúng trang, Stop khi trang tải chậm, tab độc lập, mở lại trang từ History và bấm tải PDF/MP3/MP4 với nội dung khớp từng byte |

## Chạy lại

```powershell
.\test.ps1          # Server + HTTP service; chỉ cần JDK 17+
.\test.ps1 -Ui      # Thêm JavaFX; cần JavaFX 21
```

Mỗi bộ kiểm thử dùng localhost và thư mục riêng dưới `target/test-runtime`. Assertion, lỗi biên dịch hay lỗi kết nối làm script thất bại; không chuyển thành kết quả PASS/SKIP.

Ảnh chụp từ scene thật được tạo tại `target/browser-media.png`, `target/browser-response.png` và `target/request-composer.png`. Kết quả build, ảnh và dữ liệu thử được loại khỏi Git.

Đây là kiểm thử cục bộ trên Windows. Chưa thay cho việc chạy thử mạng LAN, proxy hay mọi website công cộng. JavaFX WebView là bộ máy duyệt trang của ứng dụng; bài lab không triển khai toàn bộ chức năng của Chrome.
