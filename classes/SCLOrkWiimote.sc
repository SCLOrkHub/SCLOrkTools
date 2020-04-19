SCLOrkWiimote {
	const wiimoteVendorId = 0x57e;
	const wiimoteProductIdOld = 0x306;
	const wiimoteProductIdNew = 0x330;

	classvar instance;

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
	var lastFirstButtonByte;
	var lastSecondButtonByte;

	var <isUsingAccelerometer;
	var <accelerometerBus;

	// Wiimote Status
	var <isBatteryLow;
	var <batteryLevel;
	var <leds;
	var <rumble;
	var <buttonStates;
	var <xAccel;
	var <yAccel;
	var <zAccel;

	var <>onButton;

	*prGetAttachedWiimotes {
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
		^SCLOrkWiimote.prGetAttachedWiimotes.notNil;
	}

	*new {
		var deviceList, productId, path, hid;
		if (instance.notNil, {
			^instance;
		});

		deviceList = SCLOrkWiimote.prGetAttachedWiimotes;
		if (deviceList.size == 0, {
			^nil;
		});
		productId = deviceList.values.at(0).productID;
		path = deviceList.values.at(0).path;

		hid = HID.open(wiimoteVendorId, productId, path);
		if (hid.isNil, {
			^nil;
		});

		instance = super.newCopyArgs(hid).init;
		^instance;
	}

	init {
		var statusFunction, acknowledgeFunction, dataFunction;

		reportingModeElements = Array.newClear(2);
		statusReportElements = Array.newClear(6);
		buttonReportElements = Array.newClear(2);
		buttonAndAccelReportElements = Array.newClear(5);
		statusSerial = 0;
		isUsingAccelerometer = false;
		rumble = false;
		batteryLevel = 0.0;
		leds = 0;

		onButton = {};

		// Build button states map and seed initial values.
		buttonStates = IdentityDictionary.new;
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
				0x11, {
					ledElement = element;
					element.repeat = true; },
				0x12, {
					reportingModeElements[element.reportIndex] = element;
					element.repeat = true;
				},
				0x15, {
					statusRequestElement = element;
					element.repeat = true;
				},
				0x20, {
					statusReportElements[element.reportIndex] = element;
					element.repeat = true;
				},
				0x30, {
					buttonReportElements[element.reportIndex] = element;
					element.repeat = true;
				},
				0x31, {
					buttonAndAccelReportElements[element.reportIndex] = element;
					element.repeat = true;
				}
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
			leds = (element.arrayValue >> 4).bitAnd(0x0f);
			isBatteryLow = (element.arrayValue.bitAnd(0x01) == 1);
			this.prResetDataReporting;
		};

		// Sixth byte of status report is battery level value.
		statusReportElements[5].action = { | value, element |
			batteryLevel = element.arrayValue / 255.0;
		};

		buttonReportElements[0].action = { | value, element |
			this.prParseFirstButtonByte(element.arrayValue);
		};

		buttonReportElements[1].action = { | value, element |
			this.prParseSecondButtonByte(element.arrayValue);
		};

		// By setting the repeat value to true we expect that the Wiimote
		// will send accelerometer updates at ~200Hz. We therefore poll
		// all values in this one status function, rather than processing
		// the bytes inidividually in each separate HIDElement action.
		buttonAndAccelReportElements[0].action = { | value, element |
			this.prParseFirstButtonByte(element.arrayValue);

			if (element.arrayValue.notNil
				and: { buttonAndAccelReportElements[1].arrayValue.notNil }
				and: { buttonAndAccelReportElements[2].arrayValue.notNil }
				and: { buttonAndAccelReportElements[3].arrayValue.notNil }
				and: { buttonAndAccelReportElements[4].arrayValue.notNil },
				{
					var xAccelRaw, yAccelRaw, zAccelRaw;

					// In this mode bits 5 and 6 of this byte contain the two
					// least significant bits of X accelerometer data.
					xAccelRaw = (
						buttonAndAccelReportElements[2].arrayValue << 2
					).bitOr(
						element.arrayValue.bitAnd(0x60) >> 5
					);
					// The Y and Z acceleration value LSb are packed into the 5th
					// and 6th bits of the second button byte, respectively. Note
					// that Y and Z only get one additional bit of precision, as
					// opposed to the two bits provided X.
					yAccelRaw = (
						buttonAndAccelReportElements[3].arrayValue << 1
					).bitOr(
						buttonAndAccelReportElements[1].arrayValue.bitAnd(0x20) >> 5
					);
					zAccelRaw = (
						buttonAndAccelReportElements[4].arrayValue << 1
					).bitOr(
						buttonAndAccelReportElements[1].arrayValue.bitAnd(0x40) >> 6
					);
					xAccel = (xAccelRaw.asFloat / 512.0) - 1.0;
					yAccel = (yAccelRaw.asFloat / 256.0) - 1.0;
					zAccel = (zAccelRaw.asFloat / 256.0) - 1.0;
					accelerometerBus.set(xAccel, yAccel, zAccel);
			});
		};

		buttonAndAccelReportElements[1].action = { | value, element |
			this.prParseSecondButtonByte(element.arrayValue);
		};

		this.prResetDataReporting;
		this.getStatus;
	}

	enableAccelerometer { |server|
		server ?? Server.default;
		isUsingAccelerometer = true;
		accelerometerBus = Bus.control(server, 3);
		this.prResetDataReporting;
	}

	disableAccelerometer {
		isUsingAccelerometer = false;
		accelerometerBus = nil;
		this.prResetDataReporting;
	}

	prResetDataReporting {
		if (isUsingAccelerometer, {
			reportingModeElements[1].value = 0x31;
			if (rumble, {
				reportingModeElements[0].value = 0x05;
			}, {
				reportingModeElements[0].value = 0x04;
			});
		}, {
			reportingModeElements[1].value = 0x30;
			if (rumble, {
				reportingModeElements[0].value = 0x01;
			}, {
				reportingModeElements[0].value = 0x00;
			});
		});
	}

	prParseFirstButtonByte { | byte |
		// Mask off non-button bytes
		byte = byte.bitAnd(0x1f);

		if (lastFirstButtonByte != byte, {
			var dPadLeft = byte.bitTest(0);
			var dPadRight = byte.bitTest(1);
			var dPadDown = byte.bitTest(2);
			var dPadUp = byte.bitTest(3);
			var plus = byte.bitTest(4);

			if (buttonStates.at(\dPadLeft) != dPadLeft, {
				buttonStates.put(\dPadLeft, dPadLeft);
				onButton.value(\dPadLeft, dPadLeft, this);
			});

			if (buttonStates.at(\dPadRight) != dPadRight, {
				buttonStates.put(\dPadRight, dPadRight);
				onButton.value(\dPadRight, dPadRight, this);
			});

			if (buttonStates.at(\dPadDown) != dPadDown, {
				buttonStates.put(\dPadDown, dPadDown);
				onButton.value(\dPadDown, dPadDown, this);
			});

			if (buttonStates.at(\dPadUp) != dPadUp, {
				buttonStates.put(\dPadUp, dPadUp);
				onButton.value(\dPadUp, dPadUp, this);
			});

			if (buttonStates.at(\plus) != plus, {
				buttonStates.put(\plus, plus);
				onButton.value(\plus, plus, this);
			});

			lastFirstButtonByte = byte;
		});
	}

	prParseSecondButtonByte { | byte |
		byte = byte.bitAnd(0x9f);

		if (lastSecondButtonByte != byte, {
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

			lastSecondButtonByte = byte;
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
		// Change to a new number, to force re-sending of byte. Outside of the
		// LSb which controls rumble, byte can be any value, just needs to be
		// sent every time.
		statusSerial = (statusSerial + 1) % 128;
	}
}
