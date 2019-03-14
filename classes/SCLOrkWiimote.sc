SCLOrkWiimote {
	const wiimoteVendorId = 0x57e;
	const wiimoteProductIdOld = 0x306;
	const wiimoteProductIdNew = 0x330;

	var hid;

	// Writable element arrays, each HIDElement object represents
	// a single byte in the communication channel.
	var ledElement;             // 0x11,
	var reportingModeElements;  // 0x12
	var statusRequestElement;   // 0x15

	// Readable Elements
	var statusReportElements;          // 0x20
	var buttonReportElements;          // 0x30
	var buttonAndAccelReportElements;  // 0x31

	var statusSerial;

	// Wiimote Status
	var <isBatteryLow;
	var <batteryLevel;
	var <leds;
	var <rumble;
	var <buttonStates;
	var <isPollingEnabled;
	var <xAccel;
	var <yAccel;
	var <zAccel;

	var <>onButton;
	var <>onAccelUpdated;

	*prGetAttachedwiimotes {
		var deviceList;

		HID.findAvailable;
		deviceList = HID.findBy(wiimoteVendorId, wiimoteProductIdOld);
		if (deviceList.size == 0, {
			deviceList = HID.findBy(wiimoteVendorId, wiimoteProductIdNew);
		});
		deviceList.postln;
		^deviceList;
	}

	*isConnected {
		^SCLOrkWiimote.prGetAttachedwiimotes.notNil;
	}

	*new { | enablePolling = true |
		var deviceList, productId, path, hid;
		deviceList = SCLOrkWiimote.prGetAttachedwiimotes;
		if (deviceList.size == 0, {
			^nil;
		});
		productId = deviceList.at(0).productID;
		path = deviceList.at(0).path;

		hid = HID.open(wiimoteVendorId, productId, path);
		if (hid.isNil, {
			^nil;
		});

		^super.newCopyArgs(hid).init(enablePolling);
	}

	init { | enablePolling |
		var statusFunction, acknowledgeFunction, dataFunction;

		reportingModeElements = Array.newClear(2);
		statusReportElements = Array.newClear(6);
		buttonReportElements = Array.newClear(2);
		buttonAndAccelReportElements = Array.newClear(5);
		statusSerial = 0;
		rumble = false;

		onButton = {};
		onAccelUpdated = {};

		// Build button states map and seed initial values.
		buttonStates = Dictionary.new;
		buttonStates.put(\dPadLeft, false);
		buttonStates.put(\dPadRight, false);
		buttonStates.put(\dPadDown, false);
		buttonStates.put(\dPadUp, false);
		buttonStates.put(\plus, false);
		buttonStates.put(\two, false);
		buttonStates.put(\one, false);
		buttonStates.put(\b, false);
		buttonStates.put(\a, false);
		buttonStates.put(\minus, false);
		buttonStates.put(\home, false);

		// Wiimote data elements enumerated and explained at
		// http://wiibrew.org/wiki/wiimote. We walk through the
		// HIDElements looking for reportIDs that match certain
		// hard-coded values.
		hid.elements.do({ | element, index |
			switch (element.reportID,
				0x11, { ledElement = element },
				0x12, { reportingModeElements[element.reportIndex] = element },
				0x15, { statusRequestElement = element },
				0x20, { statusReportElements[element.reportIndex] = element },
				0x30, { buttonReportElements[element.reportIndex] = element },
				0x31, { buttonAndAccelReportElements[element.reportIndex] = element }
			);
		});

		statusReportElements[0].action = { | value, element |
			this.prParseFirstButtonByte(element.arrayValue);
		};

		statusReportElements[1].action = { | value, element |
			this.prParseSecondButtonByte(element.arrayValue);
		};

		// Third byte of status report is LED state and battery low indicator.
		statusReportElements[2].action = { | value, element |
			// Most significant nybble are LED state bits.
			leds = (element.rawValue >> 4).bitAnd(0x0f);
			isBatteryLow = (element.rawValue.bitAnd(0x01) == 1);
		};

		// Sixth byte of status report is battery level value.
		statusReportElements[5].action = { | value, element |
			batteryLevel = element.rawValue / 255.0;
		};

		buttonReportElements[0].action = { | value, element |
			this.prParseFirstButtonByte(element.arrayValue);
		};

		buttonReportElements[1].action = { | value, element |
			this.prParseSecondButtonByte(element.arrayValue);
		};

		buttonAndAccelReportElements[0].action = { | value, element |
			this.prParseFirstButtonByte(element.arrayValue);
		};

		buttonAndAccelReportElements[1].action = { | value, element |
			this.prParseSecondButtonByte(element.arrayValue);
		};

		buttonAndAccelReportElements[2].action = { | value, element |
			xAccel = (element.arrayValue / 128.0) - 1.0;
		};

		buttonAndAccelReportElements[3].action = { | value, element |
			yAccel = (element.arrayValue / 128.0) - 1.0;
		};

		buttonAndAccelReportElements[4].action = { | value, element |
			zAccel = (element.arrayValue / 128.0) - 1.0;
		};

		this.isPollingEnabled = enablePolling;
		this.getStatus;
	}

	isPollingEnabled_ { | enabled |
		isPollingEnabled = enabled;
		if (enabled, {
			if (rumble, {
				reportingModeElements[0].value = 0x05;
			}, {
				reportingModeElements[0].value = 0x04;
			});
			reportingModeElements[1].value = 0x31;
		}, {
			if (rumble, {
				reportingModeElements[0].value = 0x01;
			}, {
				reportingModeElements[0].value = 0x00;
			});
			reportingModeElements[1].value = 0x30;
		});
	}

	prParseFirstButtonByte { | byte |
		var dPadLeft = byte.bitTest(0);
		var dPadRight = byte.bitTest(1);
		var dPadDown = byte.bitTest(2);
		var dPadUp = byte.bitTest(3);
		var plus = byte.bitTest(4);

		if (buttonStates.at(\dPadLeft) != dPadLeft, {
			buttonStates.put(\dPadLeft, dPadLeft);
			onButton.value(\dPadLeft, dPadLeft);
		});

		if (buttonStates.at(\dPadRight) != dPadRight, {
			buttonStates.put(\dPadRight, dPadRight);
			onButton.value(\dPadRight, dPadRight);
		});

		if (buttonStates.at(\dPadDown) != dPadDown, {
			buttonStates.put(\dPadDown, dPadDown);
			onButton.value(\dPadDown, dPadDown);
		});

		if (buttonStates.at(\dPadUp) != dPadUp, {
			buttonStates.put(\dPadUp, dPadUp);
			onButton.value(\dPadUp, dPadUp);
		});

		if (buttonStates.at(\plus) != plus, {
			buttonStates.put(\plus, plus);
			onButton.value(\plus, plus);
		});
	}

	prParseSecondButtonByte { | byte |
		var two = byte.bitTest(0);
		var one = byte.bitTest(1);
		var b = byte.bitTest(2);
		var a = byte.bitTest(3);
		var minus = byte.bitTest(4);
		var home = byte.bitTest(7);

		if (buttonStates.at(\two) != two, {
			buttonStates.put(\two, two);
			onButton.value(\two, two);
		});

		if (buttonStates.at(\one) != one, {
			buttonStates.put(\one, one);
			onButton.value(\one, one);
		});

		if (buttonStates.at(\b) != b, {
			buttonStates.put(\b, b);
			onButton.value(\b, b);
		});

		if (buttonStates.at(\a) != a, {
			buttonStates.put(\a, a);
			onButton.value(\a, a);
		});

		if (buttonStates.at(\minus) != minus, {
			buttonStates.put(\minus, minus);
			onButton.value(\minus, minus);
		});

		if (buttonStates.at(\home) != home, {
			buttonStates.put(\home, home);
			onButton.value(\home, home);
		});
	}

	free {
		hid.close;
	}

	leds_ { | newLeds |
		var ledCommand;
		leds = newLeds.bitAnd(0x0f);
		// LEDs are most significant nybble of LED command byte.
		ledCommand = (leds << 4).bitAnd(0xf0);
		// Maintain rumble status, LSb of command byte.
		if (rumble, {
			ledCommand = ledCommand.bitOr(0x01);
		});
		ledElement.value = ledCommand;
	}

	rumble_ { | newRumble |
		rumble = newRumble;
		// Since rumble is included in every command sent to WiiMote, we resend
		// the current LED value but include the new rumble state bit.
		this.leds = leds;
	}

	getStatus {
		if (rumble, {
			statusRequestElement.value = (statusSerial << 1).bitOr(0x01);
		}, {
			statusRequestElement.value = (statusSerial << 1).bitAnd(0xfe);
		});
		// Change to a new number, to force re-sending of byte. Byte can be any
		// value, just needs to be sent every time.
		statusSerial = (statusSerial + 1) % 128;
	}
}