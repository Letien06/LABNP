# LABNP — Lập trình mạng

Mã nguồn 5 bài lab, gồm server, client có giao diện, hướng dẫn chạy và các script kiểm thử.

| Lab | Thư mục | Nội dung |
|---|---|---|
| 1 | [Lab1-Chat](Lab1-Chat/) | Chat riêng TCP, phòng chung UDP multicast, thông báo LAN broadcast, gửi tệp, lịch sử và dashboard server |
| 2 | [Lab2-FTP](Lab2-FTP/) | FTP server/client, giao diện hai khung, đăng nhập, upload/download và database audit |
| 3 | [Lab3-Mail](Lab3-Mail/) | Ya-mail, đăng ký/đăng nhập, SMTP + POP3, SQLite và gửi thư ra ngoài qua Gmail relay |
| 4 | [Lab4-Web](Lab4-Web/) | Trình duyệt JavaFX, GET/HEAD/POST, history, upload/download và dịch vụ HTTP HTML/JSON |
| 5 | [Lab5-RMI](Lab5-RMI/) | CRUD qua Java RMI, hai node A/B, lưu dữ liệu, hàng đợi đồng bộ và dashboard |

## Chuẩn bị

- Windows và PowerShell cho các lệnh bên dưới.
- JDK 23+ và `JAVA_HOME` trỏ đến thư mục JDK; thêm `bin` của JDK vào `PATH`. Lab1, Lab2 và Lab5 dùng được JDK 17+.
- Python 3.12+ cho Lab3 và Lab4.
- Maven cho lần tải thư viện đầu tiên của Lab3. Lab4 có Maven Wrapper để tải JavaFX 21.

Mở PowerShell tại thư mục repo sau khi clone. Nếu PowerShell chặn script trong phiên hiện tại:

```powershell
Set-ExecutionPolicy -Scope Process Bypass
```

## Chạy nhanh

Chạy mỗi server và client trong cửa sổ terminal riêng, tại thư mục lab tương ứng.

### Lab1 — Chat

```powershell
cd Lab1-Chat
.\scripts\run-server.ps1 -Port 5555
# Terminal khác, tại Lab1-Chat:
.\scripts\run-client.ps1 -Host 127.0.0.1
```

### Lab2 — FTP

```powershell
cd Lab2-FTP
.\scripts\run-server.ps1 -Port 2121
# Terminal khác, tại Lab2-FTP:
.\scripts\run-client.ps1 -Host 127.0.0.1 -Port 2121
```

Tài khoản minh họa: `student` / `vku2026`.

### Lab3 — Ya-mail

```powershell
cd Lab3-Mail
mvn -f lib/pom.xml install
mvn -f client/pom.xml dependency:build-classpath '-Dmdep.outputFile=.qa-client-cp.txt'
.\run-client.ps1
```

Launcher mở server cục bộ và giao diện Ya-mail. Chọn **Đăng ký Ya-mail** để tạo tài khoản. Để gửi thư ra địa chỉ bên ngoài, mở cửa sổ soạn thư rồi chọn **Kết nối Gmail** và nhập Gmail/App Password của bạn. Cấu hình này được giữ trong bộ nhớ của phiên chạy.

### Lab4 — Web

```powershell
cd Lab4-Web
.\mvnw.cmd -q package
python web_service.py serve --port 8080
# Terminal khác, tại Lab4-Web:
.\scripts\run-client.ps1 -HomeUrl http://127.0.0.1:8080/
```

### Lab5 — RMI

```powershell
cd Lab5-RMI
.\build.cmd
.\run-node.cmd A
# Terminal khác, tại Lab5-RMI:
.\run-node.cmd B
# Terminal khác, tại Lab5-RMI:
.\run-client.cmd 127.0.0.1 1099
```

README của Lab5 hướng dẫn cấu hình địa chỉ hai máy vật lý và client trên máy ảo.

## Kiểm thử

Chạy tại thư mục từng lab:

| Lab | Lệnh |
|---|---|
| Lab1 | `.\scripts\test.ps1` |
| Lab2 | `.\scripts\test.ps1` |
| Lab3 | `.\test.ps1` hoặc `python .qa/feature_test.py` |
| Lab4 | `.\test.ps1` hoặc `.\scripts\test-features.ps1` |
| Lab5 | `.\test.cmd` hoặc `.\.qa\run-feature-checks.ps1` |

Xem [kết quả kiểm tra bản mã nguồn](KIEM-THU.md). Kiểm tra gửi Gmail thật và cấu hình hai máy vật lý/client VM cần đối chiếu trên môi trường sử dụng thực tế; kết quả localhost không xác nhận cấu hình đó.

## Dữ liệu khi chạy

Ứng dụng tự tạo dữ liệu khi khởi động. Repo không chứa database người dùng, thư thật, hội thoại, tệp đã tải lên, khóa/mật khẩu Gmail hay ảnh chụp thư cá nhân. Các tài khoản demo được ghi trong README từng lab là dữ liệu minh họa. Thư viện Maven và kết quả biên dịch được tạo lại trên máy chạy.
