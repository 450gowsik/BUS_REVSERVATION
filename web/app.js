document.addEventListener('DOMContentLoaded', () => {
    const d = document.getElementById('travelDate');
    if (d) {
        const today = new Date().toISOString().split('T')[0];
        d.min = today;
        d.value = today;

        const urlParams = new URLSearchParams(window.location.search);
        const src = urlParams.get('src');
        const dst = urlParams.get('dst');
        if (src && dst) {
            document.getElementById('fromCity').value = src;
            document.getElementById('toCity').value = dst;
            setTimeout(searchBuses, 300); // slight delay to allow UI to render first
        }
    }
});

let busList = [];

function swapCities() {
    const f = document.getElementById('fromCity');
    const t = document.getElementById('toCity');
    [f.value, t.value] = [t.value, f.value];
}

async function searchBuses() {
    const container = document.getElementById('bus-container');
    container.innerHTML = '<div class="placeholder-msg"><p>Searching for buses...</p></div>';

    try {
        const res = await fetch('http://localhost:8081/api/buses');
        busList = await res.json();
        applyFilters();
    } catch (e) {
        container.innerHTML = '<div class="placeholder-msg"><p>Could not connect to server. Make sure the backend is running on port 8081.</p></div>';
    }
}

function applyFilters() {
    const from = document.getElementById('fromCity').value.trim().toLowerCase();
    const to = document.getElementById('toCity').value.trim().toLowerCase();
    const date = document.getElementById('travelDate').value;
    const dateStr = new Date(date).toLocaleDateString('en-IN', { weekday: 'short', day: 'numeric', month: 'short', year: 'numeric' });
    const counter = document.getElementById('results-count');

    let filtered = [...busList];
    
    // 1. Text Search (From/To)
    if (from) filtered = filtered.filter(b => b.source.toLowerCase().includes(from));
    if (to) filtered = filtered.filter(b => b.destination.toLowerCase().includes(to));

    // 2. Bus Type
    const ac = document.getElementById('f-ac').checked;
    const nonac = document.getElementById('f-nonac').checked;
    filtered = filtered.filter(b => (b.ac && ac) || (!b.ac && nonac));

    // 3. Departure Time
    const t1 = document.getElementById('f-time-1').checked; // Before 6 AM
    const t2 = document.getElementById('f-time-2').checked; // 6 AM - 12 PM
    const t3 = document.getElementById('f-time-3').checked; // 12 PM - 6 PM
    const t4 = document.getElementById('f-time-4').checked; // After 6 PM
    
    filtered = filtered.filter(b => {
        const hour = parseInt(b.departure.split(':')[0]);
        if (hour < 6 && t1) return true;
        if (hour >= 6 && hour < 12 && t2) return true;
        if (hour >= 12 && hour < 18 && t3) return true;
        if (hour >= 18 && t4) return true;
        return false;
    });

    // 4. Rating
    const ratingOnly = document.getElementById('f-rating').checked;
    if (ratingOnly) {
        filtered = filtered.filter(b => b.rating >= 4.0);
    }

    // 5. Price Range
    const p1 = document.getElementById('f-price-1').checked; // < 500
    const p2 = document.getElementById('f-price-2').checked; // 500 - 1000
    const p3 = document.getElementById('f-price-3').checked; // > 1000
    
    filtered = filtered.filter(b => {
        if (b.price < 500 && p1) return true;
        if (b.price >= 500 && b.price <= 1000 && p2) return true;
        if (b.price > 1000 && p3) return true;
        return false;
    });

    // 6. Sorting
    const activeSortBtn = document.querySelector('.sort-btn.active');
    const sortKey = activeSortBtn ? activeSortBtn.textContent.trim().toLowerCase() : 'departure';
    
    if (sortKey === 'price') filtered.sort((a, b) => a.price - b.price);
    else if (sortKey === 'rating') filtered.sort((a, b) => b.rating - a.rating);
    else filtered.sort((a, b) => a.departure.localeCompare(b.departure));

    // Render & Update count
    if (counter) counter.textContent = filtered.length + ' buses found · ' + dateStr;
    renderCards(filtered);
}

function renderCards(buses) {
    const c = document.getElementById('bus-container');
    c.innerHTML = '';
    if (!buses.length) {
        c.innerHTML = '<div class="placeholder-msg"><p>No buses match this route. Try a different city pair.</p></div>';
        return;
    }
    buses.forEach(b => {
        const dur = calcDur(b.departure, b.arrival);
        const logoClass = b.ac ? 'ac-bus' : 'nonac-bus';
        const initials = b.busName.split(' ').map(w => w[0]).join('').substring(0, 2);
        const typeLabel = b.ac ? 'A/C Sleeper (2+1)' : 'Non-AC Push Back (2+2)';
        const typeTag = b.ac ? '<span class="tag tag-ac">AC Sleeper</span>' : '<span class="tag tag-nonac">Non-AC</span>';
        const ratingClass = b.rating >= 4.0 ? 'high' : '';

        const card = document.createElement('div');
        card.className = 'bus-card';
        card.innerHTML = `
            <div class="card-top">
                <div class="operator-info">
                    <div class="op-logo ${logoClass}">${initials}</div>
                    <div>
                        <div class="operator-name">${b.busName}</div>
                        <div class="operator-type">${typeLabel}</div>
                    </div>
                </div>
                <div class="card-price">
                    <span class="starts">starts from</span>
                    <div class="amount">₹${b.price.toLocaleString('en-IN')}</div>
                </div>
            </div>
            <div class="card-middle">
                <div class="time-block">
                    <div class="time-val">${b.departure}</div>
                    <div class="city-name">${b.source}</div>
                </div>
                <div class="route-visual">
                    <div class="duration-text">${dur}</div>
                    <div class="route-line"></div>
                </div>
                <div class="time-block">
                    <div class="time-val">${b.arrival}</div>
                    <div class="city-name">${b.destination}</div>
                </div>
            </div>
            <div class="card-bottom">
                <div class="card-tags">
                    ${typeTag}
                    <span class="tag tag-rating ${ratingClass}">★ ${b.rating}</span>
                    <span class="tag tag-seats">${b.capacity} seats</span>
                    <span class="tag tag-amenity">WiFi</span>
                    <span class="tag tag-amenity">Charging</span>
                </div>
                <button class="btn-select" onclick="openModal(${b.busNo},'${esc(b.busName)}','${b.source}','${b.destination}','${b.departure}',${b.price})">Select Seat</button>
            </div>`;
        c.appendChild(card);
    });
}

function esc(s) { return s.replace(/'/g, "\\'"); }

function calcDur(dep, arr) {
    const [dh, dm] = dep.split(':').map(Number);
    const [ah, am] = arr.split(':').map(Number);
    let diff = (ah * 60 + am) - (dh * 60 + dm);
    if (diff < 0) diff += 1440;
    return Math.floor(diff / 60) + 'h ' + diff % 60 + 'm';
}

function sortBuses(key) {
    document.querySelectorAll('.sort-btn').forEach(b => b.classList.remove('active'));
    event.target.classList.add('active');
    applyFilters();
}

function clearFilters() {
    document.getElementById('f-ac').checked = true;
    document.getElementById('f-nonac').checked = true;
    document.getElementById('f-time-1').checked = true;
    document.getElementById('f-time-2').checked = true;
    document.getElementById('f-time-3').checked = true;
    document.getElementById('f-time-4').checked = true;
    document.getElementById('f-rating').checked = false;
    document.getElementById('f-price-1').checked = true;
    document.getElementById('f-price-2').checked = true;
    document.getElementById('f-price-3').checked = true;
    
    // Reset Sort to default
    document.querySelectorAll('.sort-btn').forEach(b => b.classList.remove('active'));
    const depBtn = document.querySelector('.sort-btn[onclick="sortBuses(\'departure\')"]');
    if (depBtn) depBtn.classList.add('active');
    
    applyFilters();
}

let selectedBusNo = null;
let selectedBusName = null;
let selectedPrice = 0;
let selectedCapacity = 40;
let selectedSeats = [];
let bookingInProgress = false;
let pendingBookingPayload = null;

function openModal(busNo, name, src, dst, dep, price) {
    const bus = busList.find(b => b.busNo === busNo);
    selectedBusNo = busNo;
    selectedBusName = name;
    selectedPrice = price;
    selectedCapacity = bus ? bus.capacity : 40;
    selectedSeats = [];

    document.getElementById('seat-modal-info').innerHTML = `<strong>${name}</strong> · ${src} → ${dst}<br>Departure: ${dep} · ₹${price.toLocaleString('en-IN')} per seat`;
    document.getElementById('selected-seats-display').textContent = 'None';
    document.getElementById('total-fare-display').textContent = '₹0';
    document.getElementById('proceed-passenger-btn').disabled = true;

    document.getElementById('seat-layout-modal').style.display = 'block';
    document.body.style.overflow = 'hidden';

    fetchSeatsAndRender();
}

async function fetchSeatsAndRender() {
    const grid = document.getElementById('seat-grid');
    grid.innerHTML = '<div style="text-align:center; padding:30px; color:#888; grid-column:1/-1;">Loading seats...</div>';

    try {
        const dateStr = document.getElementById('travelDate').value;
        const res = await fetch(`http://localhost:8081/api/seats?busNo=${selectedBusNo}&date=${dateStr}`);
        const booked = await res.json();
        renderSeatGrid(booked);
    } catch (e) {
        grid.innerHTML = '<div style="text-align:center; color:red; grid-column:1/-1;">Failed to load seats</div>';
    }
}

function renderSeatGrid(booked) {
    const grid = document.getElementById('seat-grid');
    grid.innerHTML = '';
    const rows = Math.ceil(selectedCapacity / 4);
    const cols = ['A', 'B', 'aisle', 'C', 'D'];

    for (let r = 1; r <= rows; r++) {
        for (let c = 0; c < 5; c++) {
            const cell = document.createElement('div');
            if (cols[c] === 'aisle') {
                grid.appendChild(cell);
                continue;
            }
            const seatNo = r + cols[c];
            cell.className = 'seat seat-available';
            cell.textContent = seatNo;

            if (booked.includes(seatNo)) {
                cell.className = 'seat seat-booked';
            } else {
                cell.onclick = () => toggleSeat(cell, seatNo);
            }
            grid.appendChild(cell);
        }
    }
}

function toggleSeat(cell, seatNo) {
    if (selectedSeats.includes(seatNo)) {
        selectedSeats = selectedSeats.filter(s => s !== seatNo);
        cell.className = 'seat seat-available';
    } else {
        selectedSeats.push(seatNo);
        cell.className = 'seat seat-selected';
    }

    const display = document.getElementById('selected-seats-display');
    const fare = document.getElementById('total-fare-display');
    const btn = document.getElementById('proceed-passenger-btn');

    if (selectedSeats.length === 0) {
        display.textContent = 'None';
        fare.textContent = '₹0';
        btn.disabled = true;
    } else {
        display.textContent = selectedSeats.join(', ');
        fare.textContent = '₹' + (selectedSeats.length * selectedPrice).toLocaleString('en-IN');
        btn.disabled = false;
    }
}

function closeSeatModal() {
    document.getElementById('seat-layout-modal').style.display = 'none';
    document.body.style.overflow = '';
}

function openPassengerModalFromSeats() {
    closeSeatModal();

    document.getElementById('modalBusNo').value = selectedBusNo;
    document.getElementById('modalSeatNumbers').value = selectedSeats.join(', ');

    const date = document.getElementById('travelDate').value;
    const total = selectedSeats.length * selectedPrice;

    document.getElementById('modal-info').innerHTML =
        `<strong>${selectedBusName}</strong><br>Seats: ${selectedSeats.join(', ')} · Total: ₹${total.toLocaleString('en-IN')} · Date: ${date}`;

    // Build per-seat passenger fields
    const container = document.getElementById('passenger-fields-container');
    container.innerHTML = '';
    selectedSeats.forEach((seat, i) => {
        const block = document.createElement('div');
        block.style.cssText = 'margin-bottom:16px; padding:14px; background:#f9fafc; border-radius:10px; border:1px solid #f0f2f5;';
        block.innerHTML = `
            <div style="font-size:.72rem; font-weight:700; letter-spacing:.5px; color:#d63031; margin-bottom:10px;">PASSENGER ${i + 1} — SEAT ${seat}</div>
            <div class="form-field" style="margin-bottom:10px;">
                <label for="pName_${i}">Full Name</label>
                <input type="text" id="pName_${i}" placeholder="As per government ID" required
                    style="width:100%;padding:11px 14px;border:1.5px solid #e0e0e0;border-radius:8px;font-size:.95rem;font-family:inherit;outline:none;box-sizing:border-box;">
            </div>
            <div style="display:flex; gap:12px;">
                <div class="form-field" style="flex:1">
                    <label for="pAge_${i}">Age</label>
                    <input type="number" id="pAge_${i}" placeholder="e.g. 25" min="1" max="100" required
                        style="width:100%;padding:11px 14px;border:1.5px solid #e0e0e0;border-radius:8px;font-size:.95rem;font-family:inherit;outline:none;">
                </div>
                <div class="form-field" style="flex:1.5">
                    <label for="pGender_${i}">Gender</label>
                    <select id="pGender_${i}" required
                        style="width:100%;padding:11px 14px;border:1.5px solid #e0e0e0;border-radius:8px;font-size:.95rem;font-family:inherit;outline:none;">
                        <option value="" disabled selected>Select</option>
                        <option value="Male">Male</option>
                        <option value="Female">Female</option>
                        <option value="Other">Other</option>
                    </select>
                </div>
            </div>`;
        container.appendChild(block);
    });

    // Prefill email/phone from session
    const u = localStorage.getItem('onyxbus_user');
    document.getElementById('passengerEmail').value = '';
    document.getElementById('passengerPhone').value = '';
    if (u) {
        try {
            const user = JSON.parse(u);
            document.getElementById('passengerEmail').value = user.email || '';
            document.getElementById('passengerPhone').value = user.phone || '';
        } catch (e) { }
    }

    const m = document.getElementById('booking-message');
    m.className = 'msg-box'; m.style.display = 'none';
    document.getElementById('modal-backdrop').style.display = 'block';
    document.body.style.overflow = 'hidden';
    setTimeout(() => document.getElementById('pName_0') && document.getElementById('pName_0').focus(), 100);
}

function closeModal() {
    document.getElementById('modal-backdrop').style.display = 'none';
    document.body.style.overflow = '';
}
function closePaymentModal() {
    document.getElementById('payment-modal').style.display = 'none';
    document.body.style.overflow = '';
}
window.addEventListener('click', e => {
    if (e.target.id === 'modal-backdrop') closeModal();
    if (e.target.id === 'payment-modal') closePaymentModal();
    if (e.target.id === 'seat-layout-modal') closeSeatModal();
});

async function handleBooking(e) {
    e.preventDefault();
    
    // Collect per-seat passenger details
    const passengers = selectedSeats.map((seat, i) => ({
        name: document.getElementById(`pName_${i}`).value,
        age: parseInt(document.getElementById(`pAge_${i}`).value),
        gender: document.getElementById(`pGender_${i}`).value,
        seat: seat
    }));

    pendingBookingPayload = {
        passengers,
        email: document.getElementById('passengerEmail').value,
        phone: document.getElementById('passengerPhone').value,
        busNo: parseInt(document.getElementById('modalBusNo').value),
        seats: document.getElementById('modalSeatNumbers').value,
        date: document.getElementById('travelDate').value
    };

    // Close passenger modal and open payment modal
    closeModal();
    const total = selectedSeats.length * selectedPrice;
    document.getElementById('pay-total-amount').textContent = '₹' + total.toLocaleString('en-IN');
    document.getElementById('payment-message').style.display = 'none';
    
    // Reset to card tab when opening
    if (typeof switchPaymentTab === 'function') switchPaymentTab('card');
    const upiMsg = document.getElementById('upi-payment-message');
    if (upiMsg) upiMsg.style.display = 'none';
    const rzpMsg = document.getElementById('rzp-payment-message');
    if (rzpMsg) rzpMsg.style.display = 'none';

    document.getElementById('payment-modal').style.display = 'block';
    document.body.style.overflow = 'hidden';
}

async function processPayment(e) {
    e.preventDefault();
    if (bookingInProgress) return;
    bookingInProgress = true;

    const btn = document.getElementById('pay-now-btn');
    const msg = document.getElementById('payment-message');
    
    btn.textContent = 'Processing Payment...';
    btn.disabled = true;
    msg.style.display = 'none';

    // Simulate payment delay
    await new Promise(resolve => setTimeout(resolve, 1500));

    try {
        const res = await fetch('http://localhost:8081/api/book', {
            method: 'POST', 
            headers: { 'Content-Type': 'application/json' }, 
            body: JSON.stringify(pendingBookingPayload)
        });
        const r = await res.json();
        
        msg.className = 'msg-box ' + (r.success ? 'msg-ok' : 'msg-err');
        msg.textContent = r.message;
        msg.style.display = 'block';

        if (r.success) {
            btn.textContent = 'Success!';
            setTimeout(() => {
                closePaymentModal();
                // Optional: redirect to my-trips
                // window.location.href = '/my-trips.html';
            }, 2000);
        } else {
            btn.textContent = 'Retry Payment';
            btn.disabled = false;
        }
    } catch (err) { 
        msg.className = 'msg-box msg-err';
        msg.textContent = 'Server connection failed.'; 
        msg.style.display = 'block';
        btn.textContent = 'Retry Payment';
        btn.disabled = false;
    } finally { 
        bookingInProgress = false; 
    }
}

function switchPaymentTab(method) {
    const tabCard = document.getElementById('tab-card');
    const tabUpi = document.getElementById('tab-upi');
    const tabRazorpay = document.getElementById('tab-razorpay');
    const formCard = document.getElementById('card-form-container');
    const formUpi = document.getElementById('upi-form-container');
    const formRazorpay = document.getElementById('razorpay-form-container');

    tabCard.style.borderBottom = '2px solid transparent';
    tabCard.style.color = '#666';
    tabUpi.style.borderBottom = '2px solid transparent';
    tabUpi.style.color = '#666';
    if(tabRazorpay) {
        tabRazorpay.style.borderBottom = '2px solid transparent';
        tabRazorpay.style.color = '#666';
    }
    
    formCard.style.display = 'none';
    formUpi.style.display = 'none';
    if(formRazorpay) formRazorpay.style.display = 'none';

    if (method === 'card') {
        tabCard.style.borderBottom = '2px solid #d63031';
        tabCard.style.color = '#d63031';
        formCard.style.display = 'block';
    } else if (method === 'upi') {
        tabUpi.style.borderBottom = '2px solid #d63031';
        tabUpi.style.color = '#d63031';
        formUpi.style.display = 'block';
    } else if (method === 'razorpay') {
        if(tabRazorpay) {
            tabRazorpay.style.borderBottom = '2px solid #3395FF';
            tabRazorpay.style.color = '#3395FF';
        }
        if(formRazorpay) formRazorpay.style.display = 'block';
    }
}

async function processUPIPayment(e) {
    e.preventDefault();
    if (bookingInProgress) return;
    bookingInProgress = true;

    const btn = document.getElementById('upi-pay-now-btn');
    const msg = document.getElementById('upi-payment-message');
    const upiId = document.getElementById('upi-id-input').value.trim();
    
    btn.textContent = 'Waiting for approval on GPay...';
    btn.disabled = true;
    msg.style.display = 'none';

    // Simulate longer delay for user approving on mobile
    await new Promise(resolve => setTimeout(resolve, 3500));

    try {
        const res = await fetch('http://localhost:8081/api/book', {
            method: 'POST', 
            headers: { 'Content-Type': 'application/json' }, 
            body: JSON.stringify(pendingBookingPayload)
        });
        const r = await res.json();
        
        msg.className = 'msg-box ' + (r.success ? 'msg-ok' : 'msg-err');
        msg.textContent = r.message;
        msg.style.display = 'block';

        if (r.success) {
            btn.textContent = 'Payment Successful!';
            setTimeout(() => {
                closePaymentModal();
            }, 2000);
        } else {
            btn.textContent = 'Request Payment on GPay';
            btn.disabled = false;
        }
    } catch (err) { 
        msg.className = 'msg-box msg-err';
        msg.textContent = 'Server connection failed.'; 
        msg.style.display = 'block';
        btn.textContent = 'Request Payment on GPay';
        btn.disabled = false;
    } finally { 
        bookingInProgress = false; 
    }
}

async function processRazorpayPayment(e) {
    e.preventDefault();
    if (bookingInProgress) return;
    bookingInProgress = true;

    const btn = document.getElementById('rzp-pay-now-btn');
    const msg = document.getElementById('rzp-payment-message');
    
    btn.textContent = 'Authorizing with Razorpay...';
    btn.disabled = true;
    msg.style.display = 'none';

    // Simulate opening razorpay modal
    await new Promise(resolve => setTimeout(resolve, 1500));
    btn.textContent = 'Processing Payment...';
    
    // Simulate payment processing
    await new Promise(resolve => setTimeout(resolve, 2500));

    try {
        const res = await fetch('http://localhost:8081/api/book', {
            method: 'POST', 
            headers: { 'Content-Type': 'application/json' }, 
            body: JSON.stringify(pendingBookingPayload)
        });
        const r = await res.json();
        
        msg.className = 'msg-box ' + (r.success ? 'msg-ok' : 'msg-err');
        msg.textContent = r.message;
        msg.style.display = 'block';

        if (r.success) {
            btn.textContent = 'Payment Successful!';
            setTimeout(() => {
                closePaymentModal();
            }, 2000);
        } else {
            btn.textContent = 'Pay securely with Razorpay';
            btn.disabled = false;
        }
    } catch (err) { 
        msg.className = 'msg-box msg-err';
        msg.textContent = 'Server connection failed.'; 
        msg.style.display = 'block';
        btn.textContent = 'Pay securely with Razorpay';
        btn.disabled = false;
    } finally { 
        bookingInProgress = false; 
    }
}

function showMsg(ok, txt) {
    const m = document.getElementById('booking-message');
    m.className = 'msg-box ' + (ok ? 'msg-ok' : 'msg-err');
    m.textContent = txt; m.style.display = 'block';
}
