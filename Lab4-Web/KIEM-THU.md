# Kiểm thử bản Lab4 Java

Ngày kiểm tra **15/11/2026**, trên Windows với JDK 17 và JavaFX 21.

## Các bộ kiểm tra

| Bộ kiểm tra | Nội dung |
|---|---|
| `ServerIntegrationTest` | HTTP thật, GET/HEAD/POST, HTML, JSON, form, Unicode, upload/download nhị phân, MIME và Range của tệp mẫu, giới hạn upload và xử lý lỗi |
| `HttpServiceIntegrationTest` | HTTP bất đồng bộ, tên tệp Unicode, tải song song, tiến độ, giữ tệp đã có, hủy tải và dọn tệp tạm |
| `BrowsingHistoryTest` | Đọc lại lịch sử từ tệp, giữ thứ tự, bỏ qua dòng hỏng và duy trì trạng thái sau khi xóa |
| `BrowserFunctionalTest` | 34 kiểm tra JavaFX, gồm Enter để tìm kiếm từ thanh địa chỉ, mã hóa từ khóa tiếng Việt, mở địa chỉ cục bộ, Reload/Stop, tab độc lập, History và tải PDF/MP3/MP4 khớp từng byte |

## Chạy lại

```powershell
.\test.ps1          # Server + HTTP service; chỉ cần JDK 17+
.\test.ps1 -Ui      # Thêm JavaFX; cần JavaFX 21
```

Mỗi bộ kiểm thử dùng localhost và thư mục riêng dưới `target/test-runtime`. Assertion, lỗi biên dịch hay lỗi kết nối làm script thất bại; không chuyển thành kết quả PASS/SKIP.

Ảnh chụp từ scene thật được tạo tại `target/browser-media.png`, `target/browser-response.png` và `target/request-composer.png`. Kết quả build, ảnh và dữ liệu thử được loại khỏi Git.

Các bộ kiểm thử tự động dùng localhost. Khi thử thủ công trên Windows, nhập `lập trình mạng Java` và nhấn Enter đã hiển thị kết quả Google trong tab Lab4. Mức tương thích với website công cộng tùy thuộc JavaFX WebView.
