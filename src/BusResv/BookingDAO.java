package BusResv;

import java.util.Date;
import java.util.ArrayList;
import java.util.List;
import java.sql.*;

public class BookingDAO {

	public int getBookedCount(int busNo, Date date) throws SQLException {

		String query = "select count(passenger_name) from booking where bus_no=? and travel_date=?";
		Connection con = DbConnection.getConnection();
		PreparedStatement pst = con.prepareStatement(query);
		java.sql.Date sqldate = new java.sql.Date(date.getTime());
		pst.setInt(1, busNo);
		pst.setDate(2, sqldate);
		ResultSet rs = pst.executeQuery();
		rs.next();
		return rs.getInt(1);
	}

	public void addBooking(Booking booking) throws SQLException {
		String query = "Insert into booking (passenger_name, bus_no, travel_date, age, gender, email, phone, seat_numbers) values(?,?,?,?,?,?,?,?)";
		Connection con = DbConnection.getConnection();
		java.sql.Date sqldate = new java.sql.Date(booking.date.getTime());
		PreparedStatement pst = con.prepareStatement(query);
		pst.setString(1, booking.passengerName);
		pst.setInt(2, booking.busNo);
		pst.setDate(3, sqldate);
		pst.setInt(4, booking.age);
		pst.setString(5, booking.gender);
		pst.setString(6, booking.email);
		pst.setString(7, booking.phone);
		pst.setString(8, booking.seatNumbers != null ? booking.seatNumbers : "");

		pst.executeUpdate();
	}

	public boolean areSeatsTaken(int busNo, String dateStr, String seatNumbers) throws SQLException {
		if (seatNumbers == null || seatNumbers.isEmpty()) return false;
		List<String> bookedSeats = getBookedSeats(busNo, dateStr);
		String[] requested = seatNumbers.split(",");
		for (String s : requested) {
			if (bookedSeats.contains(s.trim())) return true;
		}
		return false;
	}

	public List<String> getBookedSeats(int busNo, String dateStr) throws SQLException {
		List<String> bookedSeats = new ArrayList<>();
		String query = "SELECT seat_numbers FROM booking WHERE bus_no=? AND travel_date=?";
		Connection con = DbConnection.getConnection();
		PreparedStatement pst = con.prepareStatement(query);
		pst.setInt(1, busNo);
		pst.setString(2, dateStr);
		ResultSet rs = pst.executeQuery();
		while (rs.next()) {
			String seats = rs.getString(1);
			if (seats != null && !seats.isEmpty()) {
				String[] seatArray = seats.split(",");
				for (String s : seatArray) {
					bookedSeats.add(s.trim());
				}
			}
		}
		return bookedSeats;
	}

	public List<String[]> getBookingsByName(String name) throws SQLException {
		List<String[]> results = new ArrayList<>();
		String query = "SELECT b.passenger_name, b.bus_no, b.travel_date, " +
				"COALESCE(bus.bus_name,'Unknown'), COALESCE(bus.source,'N/A'), " +
				"COALESCE(bus.destination,'N/A'), COALESCE(bus.departure,'N/A'), " +
				"COALESCE(bus.price,0), b.age, b.gender, b.email, b.phone " +
				"FROM booking b LEFT JOIN bus ON b.bus_no = bus.id " +
				"WHERE LOWER(b.passenger_name) LIKE LOWER(?) ORDER BY b.travel_date DESC";
		Connection con = DbConnection.getConnection();
		PreparedStatement pst = con.prepareStatement(query);
		pst.setString(1, "%" + name + "%");
		ResultSet rs = pst.executeQuery();
		while (rs.next()) {
			results.add(new String[] {
					rs.getString(1), String.valueOf(rs.getInt(2)),
					rs.getString(3), rs.getString(4),
					rs.getString(5), rs.getString(6),
					rs.getString(7), String.valueOf(rs.getInt(8)),
					String.valueOf(rs.getInt(9)), rs.getString(10),
					rs.getString(11), rs.getString(12)
			});
		}
		return results;
	}

	public boolean cancelBooking(String name, int busNo, String dateStr) throws SQLException {
		String query = "DELETE FROM booking WHERE passenger_name=? AND bus_no=? AND travel_date=? LIMIT 1";
		Connection con = DbConnection.getConnection();
		PreparedStatement pst = con.prepareStatement(query);
		pst.setString(1, name);
		pst.setInt(2, busNo);
		pst.setString(3, dateStr);
		int rows = pst.executeUpdate();
		return rows > 0;
	}
}
