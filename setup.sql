CREATE DATABASE IF NOT EXISTS jdbcdemo;
USE jdbcdemo;

CREATE TABLE IF NOT EXISTS employee (
    emp_id INT PRIMARY KEY,
    name VARCHAR(50),
    salary INT
);

INSERT IGNORE INTO employee (emp_id, name, salary) VALUES 
(1, 'Alice', 100000),
(2, 'Bob', 120000),
(3, 'Charlie', 150000);

DELIMITER //
CREATE PROCEDURE IF NOT EXISTS GetEmp()
BEGIN
    SELECT * FROM employee;
END //

CREATE PROCEDURE IF NOT EXISTS GetEmpById(IN id INT)
BEGIN
    SELECT * FROM employee WHERE emp_id = id;
END //

CREATE PROCEDURE IF NOT EXISTS GetNameById(IN id INT, OUT emp_name VARCHAR(50))
BEGIN
    SELECT name INTO emp_name FROM employee WHERE emp_id = id;
END //
DELIMITER ;


CREATE DATABASE IF NOT EXISTS busresv;
USE busresv;

DROP TABLE IF EXISTS booking;
DROP TABLE IF EXISTS bus;

CREATE TABLE IF NOT EXISTS bus (
    id INT PRIMARY KEY,
    ac INT,
    capacity INT,
    source VARCHAR(50) DEFAULT 'Chennai',
    destination VARCHAR(50) DEFAULT 'Bangalore',
    departure VARCHAR(10) DEFAULT '21:00',
    arrival VARCHAR(10) DEFAULT '06:00',
    price INT DEFAULT 650,
    rating DECIMAL(2,1) DEFAULT 4.0,
    bus_name VARCHAR(100) DEFAULT 'Onyx Travels'
);

CREATE TABLE IF NOT EXISTS booking (
    passenger_name VARCHAR(50),
    bus_no INT,
    travel_date DATE,
    age INT,
    gender VARCHAR(10),
    email VARCHAR(100),
    phone VARCHAR(20),
    seat_numbers VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS users (
    id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(100) NOT NULL UNIQUE,
    phone VARCHAR(20),
    password VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO bus (id, ac, capacity, source, destination, departure, arrival, price, rating, bus_name) VALUES 
(1, 1, 36, 'Chennai', 'Bangalore', '21:00', '05:30', 1200, 4.5, 'KPN Travels'),
(2, 0, 50, 'Chennai', 'Bangalore', '22:00', '06:00', 650, 3.8, 'SRS Travels'),
(3, 1, 40, 'Chennai', 'Coimbatore', '20:00', '04:30', 950, 4.2, 'Parveen Travels'),
(4, 1, 35, 'Chennai', 'Madurai', '21:30', '05:00', 1100, 4.7, 'SRM Travels'),
(5, 0, 45, 'Bangalore', 'Hyderabad', '19:00', '06:30', 800, 3.5, 'Orange Travels'),
(6, 1, 30, 'Chennai', 'Pondicherry', '06:00', '09:30', 450, 4.0, 'Thiruvalluvar Trans'),
(7, 0, 55, 'Madurai', 'Chennai', '22:00', '06:00', 550, 3.9, 'Anand Vihar'),
(8, 1, 20, 'Bangalore', 'Mysore', '08:00', '11:30', 380, 4.6, 'KSRTC Premium'),
(9, 1, 40, 'Coimbatore', 'Chennai', '21:00', '05:30', 900, 4.1, 'Kaveri Travels'),
(10, 0, 60, 'Hyderabad', 'Bangalore', '20:30', '06:00', 750, 3.6, 'Kesineni Travels'),
(11, 1, 32, 'Chennai', 'Tirupati', '05:00', '08:30', 500, 4.3, 'APSRTC Garuda'),
(12, 0, 40, 'Bangalore', 'Goa', '18:00', '06:00', 1400, 4.0, 'VRL Travels'),
(13, 1, 25, 'Chennai', 'Trichy', '23:00', '04:00', 600, 4.4, 'Raj Travels');
