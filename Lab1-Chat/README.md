# Lab1-Chat

Ứng dụng Java 17 minh họa ba kiểu giao tiếp mạng trong một giao diện Swing:

* **Tin nhắn riêng:** TCP unicast qua server, có tin nhắn, emoji, gửi tệp và lịch sử riêng giữa hai người.
* **Phòng chung:** UDP multicast trực tiếp giữa các client đã tham gia cùng một nhóm.
* **Thông báo LAN:** UDP broadcast trực tiếp tới các máy đang lắng nghe trên cùng mạng.

UDP không đi qua server TCP. Server chỉ cấp danh tính, danh sách người online và chuyển tiếp tin nhắn riêng. UDP không có hàng đợi lịch sử: rời kênh rồi tham gia lại chỉ nhận các datagram mới.

## Cổng mặc định

| Kênh | Mặc định |
|---|---|
| TCP server | `5555` |
| Phòng chung multicast | `239.255.42.99:5556` |
| Thông báo LAN broadcast | `255.255.255.255:5557` |

Địa chỉ multicast, broadcast, cổng và card mạng đều có thể đổi trong giao diện client.

## Chạy trên Windows

Mở PowerShell tại thư mục dự án:

```powershell
cd Lab1-Chat
Set-ExecutionPolicy -Scope Process Bypass
.\scripts\build.ps1
```

Mở server ở cửa sổ PowerShell thứ nhất:

```powershell
.\scripts\run-server.ps1 -Port 5555 -UdpPort 5556
```

Server mở dashboard hiển thị trạng thái, danh sách người online và sự kiện. `-NoUi` chạy server nền trong terminal:

```powershell
.\scripts\run-server.ps1 -NoUi -Port 5555
```

Mở client ở các cửa sổ khác:

```powershell
.\scripts\run-client.ps1 -Host 127.0.0.1
```

`-Host` là alias của `-ServerHost`. Có thể đổi kênh broadcast khi chạy client:

```powershell
.\scripts\run-client.ps1 -Host 192.168.1.20 `
  -UdpPort 5556 -BroadcastPort 5557 -Broadcast 192.168.1.255
```

Client yêu cầu kết nối TCP để xác nhận tên hiển thị. Phòng chung và Thông báo LAN gửi trực tiếp giữa các client. Khi ngắt kết nối hoặc mất server, giao diện tự rời các kênh UDP; kết nối lại rồi tham gia để tiếp tục.

## Luồng sử dụng trong giao diện

1. Bấm **Kết nối**, nhập tên hiển thị và địa chỉ server.
2. **Tin nhắn riêng:** bấm avatar/tên một người đang online. Nội dung được gửi bằng TCP unicast đúng tới người nhận; client thứ ba không thấy. Tệp riêng cũng đi theo kênh này.
3. **Phòng chung:** mở mục Phòng chung, chọn địa chỉ nhóm và card mạng, rồi bấm **Tham gia phòng**. Chỉ người đã tham gia cùng group/port nhận được tin mới. Bấm **Rời phòng** để ngừng nhận.
4. **Thông báo LAN:** mở mục Thông báo LAN, chọn địa chỉ broadcast/cổng, rồi bấm **Bắt đầu nghe**. Các máy cùng LAN và cùng cổng có thể nhận datagram; kênh này không tự đưa nội dung vào Tin nhắn riêng hay Phòng chung.
5. Emoji dùng được trong cả tin nhắn riêng và hai kênh UDP. Tệp hỗ trợ `.txt`, `.mp3`, `.mp4`, `.png`, `.jpg`, `.pdf`, `.wav`, `.webm`, tối đa 10 MB.

## Demo theo yêu cầu lab

* Mở sáu client với tên khác nhau và xác nhận dashboard hiển thị sáu thành viên.
* Cho A và B cùng **Tham gia phòng** multicast; cho C đứng ngoài. A gửi tin: B nhận, C không nhận.
* B bấm **Rời phòng**; A gửi thêm một tin; B tham gia lại và không thấy tin đã bỏ lỡ. Tin mới sau khi tham gia lại sẽ nhận được.
* Trong **Tin nhắn riêng**, A chọn B và gửi tin/tệp; C không thấy nội dung đó.
* Trong **Thông báo LAN**, hai máy bật cùng địa chỉ broadcast và cổng; gửi một thông báo để kiểm tra đường broadcast độc lập.

## Lưu trữ

Server lưu dữ liệu dưới thư mục `data`:

* `messages.log`: định dạng lưu cũ, giữ để tương thích; không lưu hội thoại multicast hiện tại.
* `private-messages.log`: lịch sử DM/DMFILE, luôn lọc theo đúng hai người tham gia.
* `uploads/`: tệp đã nhận và lưu trước khi chuyển tiếp cho người nhận riêng.

Tên hiển thị là danh tính phiên làm việc; ứng dụng không có hệ thống tài khoản/mật khẩu. Không đưa dữ liệu nhạy cảm vào các kênh UDP vì datagram không mã hóa.

## Kiểm tra

```powershell
.\scripts\test.ps1
```

Script kiểm tra biên dịch, sáu client TCP, danh sách `USERS`, chống trùng tên, tin/tệp chỉ đến đúng hai người, lịch sử theo cặp và đóng socket khi server dừng. Kiểm tra UDP gồm framing, multicast, broadcast, join/leave/rejoin và lọc gói trùng/sai nhóm. Kiểm tra UDP độc lập:

```powershell
.\scripts\test-udp.ps1
```

`test-network.ps1` và `test-udp.ps1` báo `SKIP socket-policy` khi Windows policy chặn socket. Assertion, lỗi kết nối thật hoặc lỗi giao thức sẽ làm test thất bại.

## Firewall và mạng LAN

Trên máy server cần cho phép TCP `5555`; trên các máy dùng UDP cần cho phép multicast `5556` và broadcast `5557`. Chạy PowerShell bằng quyền Administrator nếu cần:

```powershell
New-NetFirewallRule -DisplayName 'Lab1 Chat TCP 5555' -Direction Inbound -Protocol TCP -LocalPort 5555 -Action Allow
New-NetFirewallRule -DisplayName 'Lab1 Chat UDP 5556' -Direction Inbound -Protocol UDP -LocalPort 5556 -Action Allow
New-NetFirewallRule -DisplayName 'Lab1 Chat UDP 5557' -Direction Inbound -Protocol UDP -LocalPort 5557 -Action Allow
```

## Mã nguồn chính

* `ChatServer.java`, `ServerDashboard.java`: TCP relay, presence `USERS`, dashboard và lưu trữ.
* `ChatClient.java`: giao diện, DM/DMFILE, danh sách online và điều hướng ba kênh.
* `UdpChatChannel.java`, `UdpChannelPanel.java`: multicast/broadcast peer-to-peer, chọn card mạng, join/leave và chống trùng datagram.
* `MessageStore.java`: lưu lịch sử DM theo cặp người dùng. `HISTORY` trả `PRIVATE_HISTORY` và kết thúc bằng `HISTORY_END`; tin mới dùng `PRIVATE`.
