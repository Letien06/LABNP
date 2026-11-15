# Kiểm tra bản mã nguồn đưa lên GitHub

Ngày chạy: **10/11/2026**, Windows, tại bản làm việc riêng của repo LABNP.

| Lab | Kết quả |
|---|---|
| Lab1 Chat | Build PASS; lưu file; 6 client TCP; chống trùng tên; tin/tệp riêng và lịch sử theo cặp; multicast join/leave/rejoin, cô lập nhóm; broadcast hai chiều đều PASS |
| Lab2 FTP | Build và integration PASS; đăng nhập, PASV/LIST, upload/download 131.071 byte khớp SHA-256; chặn path traversal; users/files/audit tồn tại qua restart |
| Lab3 Mail | Bản cập nhật 15/11/2026 PASS 20 kiểm tra SMTP/POP3, 4 kiểm tra UIDL cache và kiểm thử trạng thái hộp thư trên SQLite cùng MySQL 9.6. Đã kiểm tra chuyển dữ liệu mẫu, chạy lại không ghi đè, SMTP/POP3, thư nháp và trạng thái đọc qua lần khởi động lại. Kiểm tra Swing với cầu nối mô phỏng xác nhận tải nền, tự lưu và mở lại bản nháp |
| Lab4 Web | Bản cập nhật 15/11/2026 PASS kiểm thử server, HTTP service, lưu lịch sử và 30 kiểm tra giao diện JavaFX. Gồm Reload/Stop, tab độc lập và tải PDF/MP3/MP4 bằng cách bấm trực tiếp vào liên kết. [Chi tiết](Lab4-Web/KIEM-THU.md) |
| Lab5 RMI | 41 kiểm tra với hai JVM server riêng PASS, gồm CRUD, forwarding, idempotency, restart, durable outbox và dashboard; IntegrationTest PASS |

Các thư viện đã có trong cache máy kiểm tra; quá trình tải Maven dependency từ đầu trên máy mới chưa được chạy lại. Xem README để cài công cụ và tải thư viện trước lần chạy đầu.

Lần kiểm tra này dùng tài khoản và dữ liệu tạm trên localhost, không gửi thư ra bên ngoài. Chưa kiểm tra xác nhận thư đến Gmail, nhận thư Gmail qua POP3, hoặc triển khai RMI trên hai máy vật lý với client trên máy ảo. Khi demo với giảng viên cần kiểm tra các luồng đó trên môi trường thực tế.

Ảnh chụp, log, database, thư và tệp phát sinh từ kiểm thử không được đưa vào repo công khai.
