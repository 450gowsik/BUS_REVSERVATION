package BusResv;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

public class WebServer {
    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(8081), 0);

        // Static files handler
        server.createContext("/", new StaticFileHandler());

        // API Handlers
        server.createContext("/api/buses", new BusesApiHandler());
        server.createContext("/api/seats", new SeatsApiHandler());
        server.createContext("/api/book", new BookingApiHandler());
        server.createContext("/api/bookings", new BookingsListHandler());
        server.createContext("/api/cancel", new CancelHandler());
        server.createContext("/api/register", new RegisterHandler());
        server.createContext("/api/login", new LoginHandler());
        server.createContext("/api/contact", new ContactApiHandler());

        server.setExecutor(null);
        server.start();
        System.out.println("OnyxBus Server started on http://localhost:8081");
        System.out.println("Open your browser and navigate to http://localhost:8081");
    }

    static class StaticFileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            String path = t.getRequestURI().getPath();
            if (path.equals("/")) {
                path = "/index.html";
            }

            File file = new File("web" + path);
            if (!file.exists() || file.isDirectory()) {
                t.sendResponseHeaders(404, -1);
                return;
            }

            String contentType = "text/plain";
            if (path.endsWith(".html"))
                contentType = "text/html";
            else if (path.endsWith(".css"))
                contentType = "text/css";
            else if (path.endsWith(".js"))
                contentType = "application/javascript";

            t.getResponseHeaders().set("Content-Type", contentType);
            t.sendResponseHeaders(200, file.length());
            try (OutputStream os = t.getResponseBody()) {
                Files.copy(file.toPath(), os);
            }
        }
    }

    static class BusesApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            if ("GET".equals(t.getRequestMethod())) {
                try {
                    BusDAO busDao = new BusDAO();
                    List<Bus> buses = busDao.getAllBuses();

                    StringBuilder jsonBuilder = new StringBuilder();
                    jsonBuilder.append("[");
                    for (int i = 0; i < buses.size(); i++) {
                        Bus bus = buses.get(i);
                        jsonBuilder.append("{")
                                .append("\"busNo\":").append(bus.getBusNo()).append(",")
                                .append("\"ac\":").append(bus.isAc()).append(",")
                                .append("\"capacity\":").append(bus.getCapacity()).append(",")
                                .append("\"source\":\"").append(bus.getSource()).append("\",")
                                .append("\"destination\":\"").append(bus.getDestination()).append("\",")
                                .append("\"departure\":\"").append(bus.getDeparture()).append("\",")
                                .append("\"arrival\":\"").append(bus.getArrival()).append("\",")
                                .append("\"price\":").append(bus.getPrice()).append(",")
                                .append("\"rating\":").append(bus.getRating()).append(",")
                                .append("\"busName\":\"").append(bus.getBusName()).append("\"")
                                .append("}");
                        if (i < buses.size() - 1)
                            jsonBuilder.append(",");
                    }
                    jsonBuilder.append("]");

                    String response = jsonBuilder.toString();
                    t.getResponseHeaders().set("Content-Type", "application/json");
                    t.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
                    t.sendResponseHeaders(200, response.length());
                    try (OutputStream os = t.getResponseBody()) {
                        os.write(response.getBytes());
                    }
                } catch (SQLException e) {
                    t.sendResponseHeaders(500, -1);
                }
            } else {
                t.sendResponseHeaders(405, -1);
            }
        }
    }

    static class BookingApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            if ("POST".equals(t.getRequestMethod())) {
                try {
                    InputStreamReader isr = new InputStreamReader(t.getRequestBody(), "utf-8");
                    BufferedReader br = new BufferedReader(isr);
                    StringBuilder payload = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null)
                        payload.append(line);

                    String requestStr = payload.toString();

                    int bNo = Integer.parseInt(extractJsonNum(requestStr, "busNo"));
                    String dStr = extractJsonStr(requestStr, "date");
                    String seats = extractJsonStr(requestStr, "seats"); // old format
                    String email = extractJsonStr(requestStr, "email");
                    String phone = extractJsonStr(requestStr, "phone");

                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
                    Date travelDate = sdf.parse(dStr);

                    // Parse passengers array to gather seats
                    boolean hasPassengersArray = requestStr.contains("\"passengers\"");
                    String seatsToCheck = seats;
                    if (hasPassengersArray) {
                        seatsToCheck = "";
                        int arrStart = requestStr.indexOf('[');
                        int arrEnd = requestStr.lastIndexOf(']');
                        if (arrStart != -1 && arrEnd != -1) {
                            String arrStr = requestStr.substring(arrStart + 1, arrEnd);
                            String[] items = arrStr.split("\\},\\s*\\{");
                            for (String item : items) {
                                String pSeat = extractJsonStr("{" + item + "}", "seat");
                                if (!seatsToCheck.isEmpty()) seatsToCheck += ",";
                                seatsToCheck += pSeat;
                            }
                        }
                    }

                    // Check availability
                    Booking tempBooking = new Booking();
                    tempBooking.busNo = bNo;
                    tempBooking.date = travelDate;
                    if (!tempBooking.isAvailable()) {
                        sendJson(t, 200, "{\"success\":false,\"message\":\"Sorry. Bus is full. Try another date.\"}");
                        return;
                    }

                    // Check if seats already taken
                    BookingDAO bDao = new BookingDAO();
                    if (seatsToCheck != null && !seatsToCheck.isEmpty() && bDao.areSeatsTaken(bNo, dStr, seatsToCheck)) {
                        sendJson(t, 200, "{\"success\":false,\"message\":\"One or more selected seats are already booked. Please choose different seats.\"}");
                        return;
                    }

                    // Parse passengers array: [{"name":"...","age":25,"gender":"Male","seat":"1A"}, ...]
                    if (hasPassengersArray) {
                        // Extract passengers array content between [ and ]
                        int arrStart = requestStr.indexOf('[');
                        int arrEnd = requestStr.lastIndexOf(']');
                        if (arrStart != -1 && arrEnd != -1) {
                            String arrStr = requestStr.substring(arrStart + 1, arrEnd);
                            // Split by },{
                            String[] items = arrStr.split("\\},\\s*\\{");
                            for (String item : items) {
                                item = item.replace("{", "").replace("}", "").trim();
                                String pName = extractJsonStr("{" + item + "}", "name");
                                String pSeat = extractJsonStr("{" + item + "}", "seat");
                                String pGender = extractJsonStr("{" + item + "}", "gender");
                                int pAge = 0;
                                try { pAge = Integer.parseInt(extractJsonNum("{" + item + "}", "age")); } catch (Exception ignored) {}

                                Booking booking = new Booking();
                                booking.passengerName = pName;
                                booking.busNo = bNo;
                                booking.date = travelDate;
                                booking.seatNumbers = pSeat;
                                booking.age = pAge;
                                booking.gender = pGender;
                                booking.email = email;
                                booking.phone = phone;
                                bDao.addBooking(booking);
                            }
                        }
                    } else {
                        // Fallback: single passenger (old format)
                        String pName = extractJsonStr(requestStr, "passengerName");
                        int age = 0;
                        try { age = Integer.parseInt(extractJsonNum(requestStr, "age")); } catch (Exception ignored) {}
                        String gender = extractJsonStr(requestStr, "gender");

                        Booking booking = new Booking();
                        booking.passengerName = pName;
                        booking.busNo = bNo;
                        booking.date = travelDate;
                        booking.seatNumbers = seats;
                        booking.age = age;
                        booking.gender = gender;
                        booking.email = email;
                        booking.phone = phone;
                        bDao.addBooking(booking);
                    }

                    sendJson(t, 200, "{\"success\":true,\"message\":\"Your booking is confirmed!\"}");
                } catch (Exception e) {
                    e.printStackTrace();
                    sendJson(t, 500, "{\"success\":false,\"message\":\"Invalid format or server error.\"}");
                }
            } else if ("OPTIONS".equals(t.getRequestMethod())) {
                t.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
                t.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
                t.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type,Authorization");
                t.sendResponseHeaders(204, -1);
            } else {
                t.sendResponseHeaders(405, -1);
            }
        }

        private void sendJson(HttpExchange t, int code, String json) throws IOException {
            t.getResponseHeaders().set("Content-Type", "application/json");
            t.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            t.sendResponseHeaders(code, json.getBytes("UTF-8").length);
            try (OutputStream os = t.getResponseBody()) { os.write(json.getBytes("UTF-8")); }
        }

        private String extractJsonStr(String json, String key) {
            String keyStr = "\"" + key + "\":\"";
            int start = json.indexOf(keyStr);
            if (start == -1) return "";
            start += keyStr.length();
            int end = json.indexOf("\"", start);
            return end == -1 ? "" : json.substring(start, end);
        }

        private String extractJsonNum(String json, String key) {
            String keyStr = "\"" + key + "\":";
            int start = json.indexOf(keyStr);
            if (start == -1) return "0";
            start += keyStr.length();
            int endString = json.indexOf(",", start);
            int endCurly = json.indexOf("}", start);
            int end = endString == -1 ? endCurly : Math.min(endString, endCurly);
            return end == -1 ? "0" : json.substring(start, end).trim();
        }
    }

    static class SeatsApiHandler implements HttpHandler {

        @Override
        public void handle(HttpExchange t) throws IOException {
            if ("GET".equals(t.getRequestMethod())) {
                try {
                    String query = t.getRequestURI().getQuery();
                    int busNo = 0;
                    String dateStr = "";
                    if (query != null) {
                        String[] params = query.split("&");
                        for (String param : params) {
                            if (param.startsWith("busNo=")) {
                                busNo = Integer.parseInt(param.split("=")[1]);
                            } else if (param.startsWith("date=")) {
                                dateStr = param.split("=")[1];
                            }
                        }
                    }

                    BookingDAO bDao = new BookingDAO();
                    List<String> bookedSeats = bDao.getBookedSeats(busNo, dateStr);

                    StringBuilder jsonBuilder = new StringBuilder();
                    jsonBuilder.append("[");
                    for (int i = 0; i < bookedSeats.size(); i++) {
                        jsonBuilder.append("\"").append(bookedSeats.get(i)).append("\"");
                        if (i < bookedSeats.size() - 1)
                            jsonBuilder.append(",");
                    }
                    jsonBuilder.append("]");

                    String response = jsonBuilder.toString();
                    t.getResponseHeaders().set("Content-Type", "application/json");
                    t.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
                    t.sendResponseHeaders(200, response.length());
                    try (OutputStream os = t.getResponseBody()) {
                        os.write(response.getBytes());
                    }
                } catch (Exception e) {
                    t.sendResponseHeaders(500, -1);
                }
            } else {
                t.sendResponseHeaders(405, -1);
            }
        }
    }

    // GET /api/bookings?name=XYZ
    static class BookingsListHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            t.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            if ("GET".equals(t.getRequestMethod())) {
                try {
                    String query = t.getRequestURI().getQuery(); // name=XYZ
                    String name = "";
                    if (query != null && query.startsWith("name=")) {
                        name = java.net.URLDecoder.decode(query.substring(5), "UTF-8");
                    }
                    BookingDAO dao = new BookingDAO();
                    java.util.List<String[]> bookings = dao.getBookingsByName(name);

                    StringBuilder sb = new StringBuilder("[");
                    for (int i = 0; i < bookings.size(); i++) {
                        String[] b = bookings.get(i);
                        sb.append("{")
                                .append("\"passengerName\":\"").append(b[0]).append("\",")
                                .append("\"busNo\":").append(b[1]).append(",")
                                .append("\"travelDate\":\"").append(b[2]).append("\",")
                                .append("\"busName\":\"").append(b[3]).append("\",")
                                .append("\"source\":\"").append(b[4]).append("\",")
                                .append("\"destination\":\"").append(b[5]).append("\",")
                                .append("\"departure\":\"").append(b[6]).append("\",")
                                .append("\"price\":").append(b[7])
                                .append("}");
                        if (i < bookings.size() - 1)
                            sb.append(",");
                    }
                    sb.append("]");
                    String resp = sb.toString();
                    t.getResponseHeaders().set("Content-Type", "application/json");
                    t.sendResponseHeaders(200, resp.getBytes("UTF-8").length);
                    try (OutputStream os = t.getResponseBody()) {
                        os.write(resp.getBytes("UTF-8"));
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    t.sendResponseHeaders(500, -1);
                }
            } else {
                t.sendResponseHeaders(405, -1);
            }
        }
    }

    // POST /api/cancel {passengerName, busNo, date}
    static class CancelHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            t.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            if ("POST".equals(t.getRequestMethod())) {
                try {
                    BufferedReader br = new BufferedReader(new InputStreamReader(t.getRequestBody(), "utf-8"));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null)
                        sb.append(line);
                    String json = sb.toString();

                    String name = extractStr(json, "passengerName");
                    int busNo = Integer.parseInt(extractNum(json, "busNo"));
                    String date = extractStr(json, "date");

                    BookingDAO dao = new BookingDAO();
                    boolean ok = dao.cancelBooking(name, busNo, date);
                    String resp = ok
                            ? "{\"success\":true,\"message\":\"Booking cancelled successfully.\"}"
                            : "{\"success\":false,\"message\":\"No matching booking found.\"}";
                    t.getResponseHeaders().set("Content-Type", "application/json");
                    t.sendResponseHeaders(200, resp.getBytes("UTF-8").length);
                    try (OutputStream os = t.getResponseBody()) {
                        os.write(resp.getBytes("UTF-8"));
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    String resp = "{\"success\":false,\"message\":\"Server error.\"}";
                    t.getResponseHeaders().set("Content-Type", "application/json");
                    t.sendResponseHeaders(500, resp.getBytes("UTF-8").length);
                    try (OutputStream os = t.getResponseBody()) {
                        os.write(resp.getBytes("UTF-8"));
                    }
                }
            } else if ("OPTIONS".equals(t.getRequestMethod())) {
                t.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
                t.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
                t.sendResponseHeaders(204, -1);
            } else {
                t.sendResponseHeaders(405, -1);
            }
        }

        private String extractStr(String json, String key) {
            String k = "\"" + key + "\":\"";
            int s = json.indexOf(k);
            if (s == -1)
                return "";
            s += k.length();
            return json.substring(s, json.indexOf("\"", s));
        }

        private String extractNum(String json, String key) {
            String k = "\"" + key + "\":";
            int s = json.indexOf(k);
            if (s == -1)
                return "0";
            s += k.length();
            int e1 = json.indexOf(",", s);
            int e2 = json.indexOf("}", s);
            int e = e1 == -1 ? e2 : Math.min(e1, e2);
            return json.substring(s, e).trim();
        }
    }

    // POST /api/register {name, email, phone, password}
    static class RegisterHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            t.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            if ("POST".equals(t.getRequestMethod())) {
                try {
                    BufferedReader br = new BufferedReader(new InputStreamReader(t.getRequestBody(), "utf-8"));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null)
                        sb.append(line);
                    String json = sb.toString();

                    String name = extractStr(json, "name");
                    String email = extractStr(json, "email");
                    String phone = extractStr(json, "phone");
                    String password = extractStr(json, "password");

                    UserDAO dao = new UserDAO();
                    boolean ok = dao.register(name, email, phone, password);
                    String resp = ok
                            ? "{\"success\":true,\"message\":\"Registration successful! You can now sign in.\"}"
                            : "{\"success\":false,\"message\":\"Email already registered. Please sign in.\"}";
                    t.getResponseHeaders().set("Content-Type", "application/json");
                    t.sendResponseHeaders(200, resp.getBytes("UTF-8").length);
                    try (OutputStream os = t.getResponseBody()) {
                        os.write(resp.getBytes("UTF-8"));
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    String resp = "{\"success\":false,\"message\":\"Server error.\"}";
                    t.getResponseHeaders().set("Content-Type", "application/json");
                    t.sendResponseHeaders(500, resp.getBytes("UTF-8").length);
                    try (OutputStream os = t.getResponseBody()) {
                        os.write(resp.getBytes("UTF-8"));
                    }
                }
            } else if ("OPTIONS".equals(t.getRequestMethod())) {
                t.getResponseHeaders().set("Access-Control-Allow-Methods", "POST, OPTIONS");
                t.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
                t.sendResponseHeaders(204, -1);
            } else {
                t.sendResponseHeaders(405, -1);
            }
        }

        private String extractStr(String json, String key) {
            String k = "\"" + key + "\":\"";
            int s = json.indexOf(k);
            if (s == -1)
                return "";
            s += k.length();
            return json.substring(s, json.indexOf("\"", s));
        }
    }

    // POST /api/login {email, password}
    static class LoginHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            t.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            if ("POST".equals(t.getRequestMethod())) {
                try {
                    BufferedReader br = new BufferedReader(new InputStreamReader(t.getRequestBody(), "utf-8"));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null)
                        sb.append(line);
                    String json = sb.toString();

                    String email = extractStr(json, "email");
                    String password = extractStr(json, "password");

                    UserDAO dao = new UserDAO();
                    String[] user = dao.login(email, password);
                    String resp;
                    if (user != null) {
                        resp = "{\"success\":true,\"message\":\"Login successful!\"," +
                                "\"user\":{\"id\":" + user[0] + ",\"name\":\"" + user[1] +
                                "\",\"email\":\"" + user[2] + "\",\"phone\":\"" + user[3] + "\"}}";
                    } else {
                        resp = "{\"success\":false,\"message\":\"Invalid email or password.\"}";
                    }
                    t.getResponseHeaders().set("Content-Type", "application/json");
                    t.sendResponseHeaders(200, resp.getBytes("UTF-8").length);
                    try (OutputStream os = t.getResponseBody()) {
                        os.write(resp.getBytes("UTF-8"));
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    String resp = "{\"success\":false,\"message\":\"Server error.\"}";
                    t.getResponseHeaders().set("Content-Type", "application/json");
                    t.sendResponseHeaders(500, resp.getBytes("UTF-8").length);
                    try (OutputStream os = t.getResponseBody()) {
                        os.write(resp.getBytes("UTF-8"));
                    }
                }
            } else if ("OPTIONS".equals(t.getRequestMethod())) {
                t.getResponseHeaders().set("Access-Control-Allow-Methods", "POST, OPTIONS");
                t.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
                t.sendResponseHeaders(204, -1);
            } else {
                t.sendResponseHeaders(405, -1);
            }
        }

        private String extractStr(String json, String key) {
            String k = "\"" + key + "\":\"";
            int s = json.indexOf(k);
            if (s == -1)
                return "";
            s += k.length();
            return json.substring(s, json.indexOf("\"", s));
        }
    }

    static class ContactApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            if ("POST".equals(t.getRequestMethod())) {
                try {
                    InputStreamReader isr = new InputStreamReader(t.getRequestBody(), "utf-8");
                    BufferedReader br = new BufferedReader(isr);
                    StringBuilder payload = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) payload.append(line);
                    String requestStr = payload.toString();

                    String name = extractStr(requestStr, "name");
                    String email = extractStr(requestStr, "email");
                    String subject = extractStr(requestStr, "subject");
                    String message = extractStr(requestStr, "message");

                    java.sql.Connection con = DbConnection.getConnection();
                    String query = "INSERT INTO contact_messages (name, email, subject, message) VALUES (?, ?, ?, ?)";
                    java.sql.PreparedStatement pst = con.prepareStatement(query);
                    pst.setString(1, name);
                    pst.setString(2, email);
                    pst.setString(3, subject);
                    pst.setString(4, message);
                    pst.executeUpdate();
                    con.close();

                    String responseJson = "{\"success\":true,\"message\":\"Your message has been received!\"}";
                    t.getResponseHeaders().set("Content-Type", "application/json");
                    t.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
                    t.sendResponseHeaders(200, responseJson.length());
                    try (OutputStream os = t.getResponseBody()) {
                        os.write(responseJson.getBytes());
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    String responseJson = "{\"success\":false,\"message\":\"Server error.\"}";
                    t.getResponseHeaders().set("Content-Type", "application/json");
                    t.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
                    t.sendResponseHeaders(500, responseJson.length());
                    try (OutputStream os = t.getResponseBody()) { os.write(responseJson.getBytes()); }
                }
            } else if ("OPTIONS".equals(t.getRequestMethod())) {
                t.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
                t.getResponseHeaders().set("Access-Control-Allow-Methods", "POST, OPTIONS");
                t.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
                t.sendResponseHeaders(204, -1);
            } else {
                t.sendResponseHeaders(405, -1);
            }
        }
        
        private String extractStr(String json, String key) {
            String k = "\"" + key + "\":\"";
            int s = json.indexOf(k);
            if (s == -1) return "";
            s += k.length();
            int e = json.indexOf("\"", s);
            if (e == -1) return "";
            return json.substring(s, e).replace("\\n", "\n").replace("\\r", "").replace("\\\"", "\"");
        }
    }
}
