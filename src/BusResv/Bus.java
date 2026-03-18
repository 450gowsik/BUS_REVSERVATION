package BusResv;

public class Bus {
	private int busNo;
	private boolean ac;
	private int capacity;
	private String source;
	private String destination;
	private String departure;
	private String arrival;
	private int price;
	private double rating;
	private String busName;

	Bus(int no, boolean ac, int cap) {
		this.busNo = no;
		this.ac = ac;
		this.capacity = cap;
	}

	Bus(int no, boolean ac, int cap, String source, String destination,
			String departure, String arrival, int price, double rating, String busName) {
		this.busNo = no;
		this.ac = ac;
		this.capacity = cap;
		this.source = source;
		this.destination = destination;
		this.departure = departure;
		this.arrival = arrival;
		this.price = price;
		this.rating = rating;
		this.busName = busName;
	}

	public int getBusNo() {
		return busNo;
	}

	public boolean isAc() {
		return ac;
	}

	public int getCapacity() {
		return capacity;
	}

	public String getSource() {
		return source != null ? source : "Chennai";
	}

	public String getDestination() {
		return destination != null ? destination : "Bangalore";
	}

	public String getDeparture() {
		return departure != null ? departure : "21:00";
	}

	public String getArrival() {
		return arrival != null ? arrival : "06:00";
	}

	public int getPrice() {
		return price > 0 ? price : 650;
	}

	public double getRating() {
		return rating > 0 ? rating : 4.0;
	}

	public String getBusName() {
		return busName != null ? busName : "Onyx Travels";
	}

	public void setAc(boolean val) {
		ac = val;
	}

	public void setCapacity(int cap) {
		capacity = cap;
	}
}
