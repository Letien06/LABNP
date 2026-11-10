# Kiểm tra bản mã nguồn đưa lên GitHub

Ngày chạy: **10/11/2026**, Windows, tại bản làm việc riêng của repo LABNP.

| Lab | Kết quả |
|---|---|
| Lab1 Chat | Build PASS; lưu file; 6 client TCP; chống trùng tên; tin/tệp riêng và lịch sử theo cặp; multicast join/leave/rejoin, cô lập nhóm; broadcast hai chiều đều PASS |
| Lab2 FTP | Build và integration PASS; đăng nhập, PASV/LIST, upload/download 131.071 byte khớp SHA-256; chặn path traversal; users/files/audit tồn tại qua restart |
| Lab3 Mail | Biên dịch Java client/lib và Python PASS; 20 kiểm tra SMTP/POP3/SQLite qua socket thật PASS, gồm xác thực, UIDL, xóa/rollback, bridge GUI và restart |
| Lab4 Web | Build JavaFX PASS; 9 kiểm tra HTTP/file/history và 8 kiểm tra controller JavaFX thực đều PASS |
| Lab5 RMI | 41 kiểm tra với hai JVM server riêng PASS, gồm CRUD, forwarding, idempotency, restart, durable outbox và dashboard; IntegrationTest PASS |

Các thư viện đã có trong cache máy kiểm tra; quá trình tải Maven dependency từ đầu trên máy mới chưa được chạy lại. Xem README để cài công cụ và tải thư viện trước lần chạy đầu.

Lần kiểm tra này dùng tài khoản và dữ liệu tạm trên localhost, không gửi thư ra bên ngoài. Chưa kiểm tra xác nhận thư đến Gmail, nhận thư Gmail qua POP3, hoặc triển khai RMI trên hai máy vật lý với client trên máy ảo. Khi demo với giảng viên cần kiểm tra các luồng đó trên môi trường thực tế.

Ảnh chụp, log, database, thư và tệp phát sinh từ kiểm thử không được đưa vào repo công khai.
