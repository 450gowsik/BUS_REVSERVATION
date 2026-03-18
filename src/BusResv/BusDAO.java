package BusResv;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class BusDAO {
	public void displayBusInfo() throws SQLException {
		String query = "Select * from bus";
		Connection con = DbConnection.getConnection();
		Statement st = con.createStatement();
		ResultSet rs = st.executeQuery(query);

		while (rs.next()) {
			System.out.println("Bus No: " + rs.getInt(1));
			if (rs.getInt(2) == 0)
				System.out.println("AC: no ");
			else
				System.out.println("AC: yes ");
			System.out.println("Capacity: " + rs.getInt(3));
		}

		System.out.println("------------------------------------------");
	}

	public int getCapacity(int id) throws SQLException {
		String query = "Select capacity from bus where id=" + id;
		Connection con = DbConnection.getConnection();
		Statement st = con.createStatement();
		ResultSet rs = st.executeQuery(query);
		if (rs.next()) {
			return rs.getInt(1);
		}
		return 0;
	}

	public List<Bus> getAllBuses() throws SQLException {
		List<Bus> buses = new ArrayList<>();
		String query = "Select id, ac, capacity, source, destination, departure, arrival, price, rating, bus_name from bus";
		Connection con = DbConnection.getConnection();
		Statement st = con.createStatement();
		ResultSet rs = st.executeQuery(query);

		while (rs.next()) {
			int id = rs.getInt("id");
			boolean ac = rs.getInt("ac") != 0;
			int capacity = rs.getInt("capacity");
			String source = rs.getString("source");
			String destination = rs.getString("destination");
			String departure = rs.getString("departure");
			String arrival = rs.getString("arrival");
			int price = rs.getInt("price");
			double rating = rs.getDouble("rating");
			String busName = rs.getString("bus_name");
			buses.add(new Bus(id, ac, capacity, source, destination, departure, arrival, price, rating, busName));
		}
		return buses;
	}
}
