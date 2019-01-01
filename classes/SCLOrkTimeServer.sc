// Clients send the server their current time as supplied by Main.elapsedTime
// and any previously computed diff.
// Servers receive this and compute a diff from the client time by subtracting it
// from their authoritative time stamp. The diff is then sent back to the client.
// Clients receive the diff, note the time, and try to use the diff and the receipt time
// to compute both an update to their local diff and the estimation of the round-trip-time.

SCLOrkTimeServer {
	const <defaultOscPath = '/getTimeDiff';
	const <defaultOscPort = 7701;
	var oscPath;
	var oscPort;
	var <>serverTestingOffset;
	var getTimeBaseOscFunc;

	*new { | oscPath, oscPort |
		^super.newCopyArgs(oscPath, oscPort).init;
	}

	init {
		if (oscPath.isNil, { oscPath = defaultOscPath; });
		if (oscPort.isNil, { oscPort = defaultOscPort; });
		serverTestingOffset = 0.0;

		getTimeBaseOscFunc = OSCFunc({| msg, time, addr |
			var serverTime, clientTime, clientPath, clientPort, timeDiff, returnAddr;
			serverTime = Main.elapsedTime + serverTestingOffset;
			clientTime = Float.from64Bits(msg[1], msg[2]);
			clientPath = msg[3];
			clientPort = msg[4];
			timeDiff = serverTime - clientTime;
			returnAddr = NetAddr.new(addr.ip, clientPort);
			returnAddr.sendMsg(clientPath, timeDiff.high32Bits, timeDiff.low32Bits);
		}, oscPath, recvPort: oscPort).permanent_(true);
	}

	free {
		getTimeBaseOscFunc.free;
	}
}