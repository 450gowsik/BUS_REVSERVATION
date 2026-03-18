package BusResv;

import java.sql.*;

public class UserDAO {

    public boolean emailExists(String email) throws SQLException {
        String query = "SELECT COUNT(*) FROM users WHERE email=?";
        Connection con = DbConnection.getConnection();
        PreparedStatement pst = con.prepareStatement(query);
        pst.setString(1, email);
        ResultSet rs = pst.executeQuery();
        rs.next();
        return rs.getInt(1) > 0;
    }

    public boolean register(String name, String email, String phone, String password) throws SQLException {
        if (emailExists(email))
            return false;
        String query = "INSERT INTO users (name, email, phone, password) VALUES (?, ?, ?, ?)";
        Connection con = DbConnection.getConnection();
        PreparedStatement pst = con.prepareStatement(query);
        pst.setString(1, name);
        pst.setString(2, email);
        pst.setString(3, phone);
        pst.setString(4, password);
        pst.executeUpdate();
        return true;
    }

    public String[] login(String email, String password) throws SQLException {
        String query = "SELECT id, name, email, phone FROM users WHERE email=? AND password=?";
        Connection con = DbConnection.getConnection();
        PreparedStatement pst = con.prepareStatement(query);
        pst.setString(1, email);
        pst.setString(2, password);
        ResultSet rs = pst.executeQuery();
        if (rs.next()) {
            return new String[] {
                    String.valueOf(rs.getInt("id")),
                    rs.getString("name"),
                    rs.getString("email"),
                    rs.getString("phone")
            };
        }
        return null;
    }
}
