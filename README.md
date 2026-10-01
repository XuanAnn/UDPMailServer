# JAVA UDP MAIL SERVER & DESKTOP MAIL CLIENT

> **Hệ thống gửi & nhận thư nội bộ sử dụng giao thức UDP tin cậy với Java Swing GUI**

Dự án gồm 2 ứng dụng viết bằng Java Swing:
1. **Server Manager UI**: Bảng điều khiển quản trị máy chủ mail UDP, giám sát socket, trạng thái dịch vụ (Account, Auth, Mail Service), log thời gian thực chi tiết (hỗ trợ demo), thống kê số thư gửi/nhận, quản lý tài khoản và cấu hình mạng.
2. **Desktop Mail Client**: Ứng dụng email máy khách giao diện hiện đại 3 cột (Sidebar hộp thư, danh sách thư có tìm kiếm, vùng đọc chi tiết và hộp soạn thư pop-up), kết nối phi chặn (non-blocking) qua mạng UDP.

---

## 1. Kiến trúc hệ thống & Cấu trúc thư mục

Hệ thống được thiết kế theo mô hình phân lớp rõ ràng:
`UI (Swing) -> Client Service (Async) -> UDP Transport/Protocol -> Server Dispatcher -> Business Services -> File Repository (Disk Persistence)`

```
src/main/java/org/example/
├── Main.java                          # Menu launcher chọn Server Manager hoặc Mail Client
├── mail/
│   ├── ServerMain.java                # Entry point chạy trực tiếp Server Manager
│   ├── ClientMain.java                # Entry point chạy trực tiếp Mail Client
│   ├── common/                        # Lớp giao thức dùng chung
│   │   ├── Protocol.java              # Định nghĩa hằng số lệnh, trạng thái, kích thước gói, cổng mặc định
│   │   ├── Request.java               # Đối tượng gói tin yêu cầu (Version, RequestId, Command, Token, Payload)
│   │   ├── Response.java              # Đối tượng gói tin phản hồi (RequestId, Status, Message, Payload)
│   │   ├── MessageCodec.java          # Bộ mã hóa/giải mã UDP Base64-safe chống lỗi ngắt dòng và ký tự đặc biệt
│   │   └── Validator.java             # Kiểm tra hợp lệ username, password, subject, body, safe file path
│   ├── server/                        # Phía Máy chủ Mail
│   │   ├── core/
│   │   │   ├── MailServer.java        # Quản lý DatagramSocket, luồng nhận gói, ThreadPool, Start/Stop/Restart
│   │   │   ├── RequestDispatcher.java # Điều phối lệnh, lưu cache Idempotent chống trùng lặp, log demo chi tiết
│   │   │   ├── ServerConfig.java      # Cấu hình IP bind, Port, thư mục data, timeout, autoStart
│   │   │   └── ServerLogListener.java # Interface bắt sự kiện log của Server
│   │   ├── service/
│   │   │   ├── AuthService.java       # Quản lý phiên làm việc, cấp phát & xác thực Session Token
│   │   │   ├── AccountService.java    # Nghiệp vụ đăng ký, kiểm tra tài khoản
│   │   │   └── MailService.java       # Nghiệp vụ gửi, đọc, đánh dấu đã đọc, nháp, chuyển thùng rác, xóa thư
│   │   ├── storage/
│   │   │   ├── AccountRepository.java # Lưu trữ tài khoản trên đĩa (ghi file nguyên tử an toàn)
│   │   │   └── MailRepository.java    # Lưu trữ email theo user/folder (inbox, sent, drafts, trash)
│   │   └── ui/
│   │       ├── ServerManagerUI.java   # Bảng điều khiển Server Manager đầy đủ tính năng
│   │       ├── ServerConfigDialog.java# Hộp thoại thiết lập cấu hình IP/Port và kiểm tra tính khả dụng của Port
│   │       └── LogPanel.java          # Khung hiển thị log có lọc cấp độ (INFO/WARN/ERROR), tìm kiếm, xuất file
│   └── client/                        # Phía Máy khách Mail
│       ├── network/
│       │   ├── UdpTransport.java      # Cơ chế truyền thông tin cậy: ACK, Timeout, Retry, Request ID
│       │   └── MailClient.java        # API tầng cao gửi nhận lệnh UDP
│       ├── model/
│       │   ├── MailFolder.java        # Enum thư mục: INBOX, SENT, DRAFTS, TRASH
│       │   ├── MailItem.java          # Đối tượng thư điện tử (ID, From, To, Subject, Body, Date, ReadState)
│       │   └── UserSession.java       # Phiên đăng nhập người dùng hiện tại
│       ├── service/
│       │   ├── AsyncCallback.java     # Callback bất đồng bộ cập nhật UI Swing
│       │   ├── AuthClientService.java # Dịch vụ xác thực phía Client
│       │   └── MailClientService.java # Tác vụ mạng nền không gây treo giao diện (No EDT blocking)
│       └── ui/
│           ├── LoginFrame.java        # Cửa sổ Đăng nhập / Đăng ký tài khoản và Ping Server
│           ├── MainFrame.java         # Cửa sổ chính giao diện 3 cột, trạng thái mạng, đếm thư chưa đọc
│           ├── SidebarPanel.java      # Thanh sidebar (Nút Soạn thư, Inbox, Sent, Drafts, Trash)
│           ├── MessageListPanel.java  # Danh sách thư với preview, tìm kiếm lọc trực tiếp
│           ├── ReadingPanel.java      # Khung đọc thư (Subject, From, To, Date, Body, Trả lời, Chuyển tiếp, Xóa)
│           └── ComposeDialog.java     # Cửa sổ Soạn thư (Gửi, Lưu bản nháp, Cảnh báo chưa lưu khi đóng)
```

---

## 2. Đặc tả giao thức UDP tin cậy (Reliable UDP Protocol)

Vì UDP là giao thức phi kết nối (connectionless) và không bảo đảm độ tin cậy, hệ thống đã tự xây dựng cơ chế bảo đảm độ tin cậy ở tầng ứng dụng:

1. **Request ID duy nhất (`UUID`)**: Mỗi yêu cầu từ Client được gán một `requestId` duy nhất.
2. **Cơ chế Timeout & Retry có giới hạn**:
   - Khi Client gửi gói tin, `UdpTransport` đặt `socket.setSoTimeout(4000ms)`.
   - Nếu quá thời gian chờ mà chưa nhận được ACK/Response khớp với `requestId`, Client sẽ tự động gửi lại (tối đa 3 lần).
3. **Chống trùng lặp tại Server (Idempotency Cache)**:
   - Server duy trì một bộ nhớ đệm `responseCache` lưu các phản hồi gần nhất theo `requestId` (thời gian sống 5 phút).
   - Nếu gói tin retry gửi đến, Server phát hiện cùng `requestId` sẽ **trả lại ngay phản hồi đã lưu**, tuyệt đối **không tạo ra email trùng lặp** trong hòm thư người nhận!
4. **Mã hóa nội dung an toàn (MessageCodec)**:
   - Dòng tiêu đề phân cách bằng tab `\t`.
   - Mọi giá trị payload (chứa tiếng Việt có dấu, xuống dòng `\n`, ký tự đặc biệt `= | &`) được mã hóa Base64 UTF-8, đảm bảo gói tin không bị vỡ định dạng khi truyền qua mạng.
5. **Kiểm tra kích thước gói**: Giới hạn tối đa của UDP datagram là 65,507 bytes; hệ thống kiểm tra kích thước payload trước khi gửi để tránh phân mảnh bất thường.

### Bảng các lệnh UDP (Commands):

| Lệnh | Mô tả | Tham số chính | Phản hồi chính |
|---|---|---|---|
| `PING` | Kiểm tra kết nối máy chủ | Không | `PONG` + `serverTime` |
| `REGISTER` | Đăng ký tài khoản mới | `username`, `password` | `OK` / `USER_EXISTS` / `INVALID` |
| `LOGIN` | Đăng nhập & cấp phiên | `username`, `password` | `OK` + `token` / `AUTH_FAILED` |
| `LOGOUT` | Thu hồi phiên làm việc | `token` | `OK` |
| `LIST` | Lấy danh sách thư theo folder | `token`, `folder` (INBOX/SENT/DRAFTS/TRASH) | `OK` + metadata danh sách thư + `unreadCount` |
| `READ` | Đọc chi tiết thư | `token`, `folder`, `mailId` | `OK` + From, To, Subject, Date, Body, Read |
| `SEND` | Gửi thư (Idempotent) | `token`, `to`, `subject`, `body` | `OK` + `mailId` |
| `SAVE_DRAFT` | Lưu bản nháp | `token`, `to`, `subject`, `body`, `mailId` | `OK` + `mailId` |
| `MARK_READ` | Đổi trạng thái đọc | `token`, `folder`, `mailId`, `read` | `OK` |
| `MOVE` | Chuyển thư sang thư mục khác | `token`, `srcFolder`, `dstFolder`, `mailId` | `OK` |
| `DELETE` | Chuyển Trash hoặc Xóa vĩnh viễn | `token`, `folder`, `mailId` | `OK` |
| `STATS` | Lấy thống kê hoạt động server | Không | `OK` + các chỉ số hiệu năng |

---

## 3. Cấu trúc lưu trữ dữ liệu bền vững (Persistence Storage)

Dữ liệu được lưu trữ trực tiếp trên đĩa theo cấu trúc thư mục của từng tài khoản:
```
data/
├── accounts/
│   ├── alice/
│   │   ├── account.txt       # Thông tin tài khoản: Username, Password, CreatedAt, Status
│   │   ├── new_email.txt     # Thư chào mừng tự động tạo khi đăng ký: "Thank you for using this service..."
│   │   ├── inbox/            # Hộp thư đến của Alice (<mailId>.txt)
│   │   ├── sent/             # Thư đã gửi của Alice (<mailId>.txt)
│   │   ├── drafts/           # Bản nháp của Alice (<mailId>.txt)
│   │   └── trash/            # Thùng rác của Alice (<mailId>.txt)
│   └── bob/
│       ├── account.txt
│       ├── new_email.txt     # Thư chào mừng tạo khi Bob đăng ký
│       ├── inbox/
│       │   └── <mailId>.txt  # Tệp email lưu rõ: From (kèm IP:Port), To, Subject, Date, ReadState, Content
│       ├── sent/
│       ├── drafts/
│       └── trash/
├── config/
│   └── server.properties     # Cấu hình IP bind, port, autoStart, emailReportEnabled
└── logs/
    └── server_YYYY-MM-DD.log # Log máy chủ được ghi tự động theo ngày
```

- **Tạo `new_email.txt` khi tạo tài khoản**: Khi người dùng tạo tài khoản mới, Server tự động tạo thư mục `data/accounts/<username>/` và tạo tệp `new_email.txt` với nội dung chuẩn:
  `“Thank you for using this service. we hope that you will feel comfortabl........”`.
- **Gửi danh sách tệp khi đăng nhập**: Khi Client đăng nhập vào một tài khoản, Server tự động mở thư mục của tài khoản đó và gửi toàn bộ danh sách tên tệp (`new_email.txt`, các file email `.txt`) về cho Client hiển thị.
- **Hiển thị tên tệp trong danh sách thư**: Mỗi email trên Client đều được đánh dấu tiền tố tên tệp tương ứng trên đĩa `📄 [new_email.txt]` hoặc `📄 [<mailId>.txt]`.

- **Ghi file nguyên tử (Atomic file writes)**: Khi lưu file tài khoản hoặc thư, dữ liệu được ghi vào tệp tạm thời `.tmp` trước khi đổi tên thay thế (`ATOMIC_MOVE`), bảo vệ dữ liệu không bị hỏng khi xảy ra mất điện hoặc dừng chương trình đột ngột.
- **Chống tấn công Path Traversal**: Tên người dùng và ID thư được lọc kiểm tra chặt chẽ bởi `Validator` (không cho phép `..`, `/`, `\`).

---

## 4. Hướng dẫn chạy và sử dụng

### Yêu cầu môi trường:
- Java JDK 21 trở lên
- Apache Maven 3.8+ (tùy chọn nếu chạy bằng lệnh Maven)

### Cách 1: Khởi chạy trực tiếp từ Terminal / Command Line

1. **Biên dịch dự án:**
   ```bash
   mvn clean package -DskipTests
   ```

2. **Chạy ứng dụng bằng file JAR:**
   - **Mở Launcher tổng hợp (cho phép chọn chạy Server hoặc Client):**
     ```bash
     java -jar target/UDPMailServer-1.0-SNAPSHOT.jar
     ```
   - **Chạy trực tiếp Server Manager:**
     ```bash
     java -cp target/UDPMailServer-1.0-SNAPSHOT.jar org.example.mail.ServerMain
     ```
   - **Chạy trực tiếp Desktop Mail Client:**
     ```bash
     java -cp target/UDPMailServer-1.0-SNAPSHOT.jar org.example.mail.ClientMain
     ```

### Cách 2: Khởi chạy trong IntelliJ IDEA:
1. Mở thư mục dự án trong IntelliJ IDEA (dự án tự động nhận diện `pom.xml`).
2. Tìm đến class mong muốn trong cây thư mục `src/main/java/org/example/`:
   - Chạy `Main.java` để mở bảng chọn.
   - Hoặc chạy `mail/ServerMain.java` để bật Server Manager.
   - Hoặc chạy `mail/ClientMain.java` để bật Mail Client (có thể chạy nhiều lần để mở 2 Client A và B đồng thời).

### Chạy bộ kiểm thử tự động (58 Test Cases):
```bash
java -cp "target/classes;target/test-classes" org.example.mail.TestSuiteRunner
```
Kết quả kiểm thử: **58/58 test cases PASSED** (Kiểm tra Codec, Validator, Phát hiện địa chỉ IPv4 LAN vật lý không qua adapter ảo, Đăng ký, Tạo tệp new_email.txt, Đăng nhập sai/đúng, Gửi thư, Lưu trữ tại `accounts/<user>/inbox`, Lưu trữ tại `accounts/<user>/sent`, Nhận thư, Ghi nhận IP/Port người gửi, Chống trùng lặp Idempotent UDP, Đánh dấu đã đọc, Thùng rác, Xóa vĩnh viễn, Đăng xuất, Lưu trữ bền vững sau khi khởi động lại Server).

---

## 5. Kịch bản Demo chi tiết cho Giảng viên / Đánh giá

### Bước 1: Khởi động Server Manager
1. Chạy `ServerMain`. Bảng điều khiển Server xuất hiện với trạng thái các dịch vụ `STOPPED`.
2. Bấm nút **▶ Start Server**.
   - Socket UDP liên kết thành công tại cổng `5000`.
   - Trạng thái `Account Service`, `Auth Service`, `Mail Service` chuyển sang `RUNNING` màu xanh lá.
   - Tab **Server Status Logs** ghi nhận sự kiện mở cổng socket.

### Bước 2: Bật 2 cửa sổ Mail Client (User A và User B)
1. Chạy `ClientMain` lần thứ nhất:
   - Chuyển sang tab **Register**, nhập username `alice`, password `123456`, bấm **Create Account**.
   - Chuyển sang tab **Sign In**, đăng nhập với `alice` / `123456`. Cửa sổ Mail Client của Alice mở ra.
2. Chạy `ClientMain` lần thứ hai:
   - Chuyển sang tab **Register**, nhập username `bob`, password `abcdef`, bấm **Create Account**.
   - Đăng nhập với `bob` / `abcdef`. Cửa sổ Mail Client của Bob mở ra.

### Bước 3: Xem Server quan sát và lưu trữ mật khẩu rõ ràng (Hỗ trợ chấm điểm Demo)
1. Trên cửa sổ **Server Manager**:
   - Chuyển sang tab **👥 Accounts & Credentials (Demo)**, bấm nút **Refresh Accounts**:
     - Bảng hiển thị danh sách tài khoản gồm `alice` (password: `123456`) và `bob` (password: `abcdef`) cùng thời gian tạo.
   - Chuyển sang tab **📋 Server Status Logs**:
     - Hiển thị đầy đủ log từng lệnh: `[REGISTER]`, `[LOGIN] User: 'alice'`, `[TOKEN]` được cấp phát.
   - Chuyển sang tab **📊 Email Report & Statistics**:
     - Thống kê tài khoản tự động nhảy lên `2`.

### Bước 4: Thử nghiệm truyền thư REALTIME (Không cần bấm Refresh)
1. Để 2 cửa sổ Client của **Alice** và **Bob** nằm cạnh nhau trên màn hình.
2. Trên Client của **Alice**:
   - Bấm nút **✏ Compose** màu xanh.
   - Nhập `To: bob`, `Subject: Demo Realtime UDP`, `Body: Chào Bob! Thư này được đẩy realtime qua UDP ngay lập tức mà Bob không cần bấm Refresh!`.
   - Bấm **📤 Send Email**. Hộp thoại báo gửi thành công.
3. Quan sát màn hình Client của **Bob**:
   - **HOÀN TOÀN KHÔNG BẤM NÚT REFRESH!**
   - Ngay lập tức (trong tích tắc), Server đẩy UDP Push Notification tới IP và Port của Bob.
   - Banner thông báo màu xanh xuất hiện: `🔔 Tin nhắn mới từ alice: Demo Realtime UDP`.
   - Danh sách thư tự động cập nhật email mới và huy hiệu số thư chưa đọc `Inbox (1)` xuất hiện ngay lập tức!

### Bước 5: Mở mail hiển thị Tên kèm IP & Port và 2 Mode mở thư (App vs TXT)
1. Trên Client của **Bob**, nhấp chọn thư vừa nhận từ Alice:
   - **Thông tin người gửi hiển thị đầy đủ Tên kèm IP và Port:**
     `From: alice  [IP: 127.0.0.1, Port: 51377]`
   - **2 Mode mở thư trực quan:**
     - **Mode 1 (Trực tiếp trên App):** Tab `📱 Mode 1: App View` hiển thị giao diện đọc thư chuẩn desktop.
     - **Mode 2 (Xem dạng tệp TXT):**
       - Có thể chọn tab `📄 Mode 2: TXT View` để xem toàn bộ header và nội dung email được định dạng chuẩn file văn bản.
       - Hoặc bấm nút **📄 Open in TXT (Notepad)** trên thanh công cụ: Chương trình sẽ tự động mở tệp `.txt` bằng trình soạn thảo văn bản mặc định của hệ điều hành (Notepad trên Windows).

### Bước 6: Kiểm tra thư mục lưu trữ trên đĩa
Mở File Explorer vào thư mục dự án `data/accounts/`:
- `data/accounts/bob/inbox/`: Chứa file `<mailId>.txt` với đầy đủ các trường `From`, `Sender-IP`, `Sender-Port`, `To`, `Subject`, `Date`, `Read`, `Folder` và `---BODY---`.
- `data/accounts/alice/sent/`: Chứa bản sao file `<mailId>.txt` của thư đã gửi.

### Bước 7: Thử nghiệm Trả lời (Reply), Chuyển tiếp (Forward) và Thùng rác (Trash)
1. Tại khung đọc email của **Bob**:
   - Bấm nút **↩ Reply**: Cửa sổ soạn thư tự động điền sẵn người nhận `alice`, tiêu đề `Re: ...` và trích dẫn nội dung cũ.
   - Bấm nút **🗑 Move to Trash**: Email được di chuyển vào thư mục `accounts/bob/trash/`.
   - Chọn thư mục **Trash**, bấm nút **❌ Delete Permanently**: Email bị xóa vĩnh viễn khỏi đĩa.

### Bước 8: Thử nghiệm tính bền vững khi Server khởi động lại (Persistence)
1. Trên Server Manager, bấm nút **⏹ Stop Server**. Trạng thái các dịch vụ chuyển về `STOPPED`.
2. Thoát Server Manager và chạy lại `ServerMain`.
3. Bấm **▶ Start Server**.
4. Mở Client và đăng nhập lại bằng `alice` và `bob`:
   - Tài khoản và toàn bộ thư mục hộp thư vẫn còn nguyên vẹn trên đĩa `data/accounts`.
#   U D P M a i l S e r v e r  
 