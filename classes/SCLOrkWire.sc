SCLOrkWire {
	const timeOut = 1.0;
	const retryCount = 5;

	var receivePort;
	var targetId;

	var selfId;
	var messageSerial;

	var netAddr;

	// \neverConnected, \connected, \failureTimeout
	var <connectionState;
	var connectionTask;

	var connectRequestOSCFunc;
	var connectAcceptOSCFunc;
	var connectConfirmOSCFunc;

	var <>onConnected;

	*new { | receivePort = 7667, targetId = 0 |
		^super.newCopyArgs(receivePort, targetId).init;
	}

	init {
		messageSerial = 0;
		connectionState = \neverConnected;

		this.prBindConnectRequest;
		this.prBindConnectAccept;
		this.prBindConnectConfirm;
	}

	prBindConnectRequest {
		connectRequestOSCFunc = OSCFunc.new({ | msg, time, addr |
			var returnPort = msg[1];
			var responderId = msg[2];
			netAddr = NetAddr.new(addr.ip, returnPort);
			selfId = responderId;
			connectionTask = Task.new({
				var retries = 0;
				connectionState = \connectionAccepted;
				this.onConnected.(connectionState);

				while ({ connectionState == \connectionAccepted and: { retries < retryCount }}, {
					netAddr.sendMsg('/wireConnectAccept', selfId, targetId);
					timeOut.wait;
					retries = retries + 1;
				});

				if (retries >= retryCount, {
					connectionState = \failureTimeout;
					this.onConnected.(connectionState);
				});
			}, SystemClock);
		},
		path: '/wireConnectRequest',
		recvPort: receivePort
		).permanent_(true);
	}

	prBindConnectAccept {
		connectAcceptOSCFunc = OSCFunc.new({ | msg, time, addr |
			var responderId = msg[1];
			var initiatorId = msg[2];
			// We only process messages intended for us as identified by
			// the responderId we provided in the connection initiation.
			if (responderId == targetId, {
				connectionState = \connected;
				// We take on the provided sender Id as our own.
				selfId = initiatorId;
				netAddr.sendMsg('/wireConnectConfirm', selfId);
				this.onConnected.(connectionState);
			});
		},
		path: '/wireConnectAccept',
		recvPort: receivePort
		).permanent_(true);
	}

	prBindConnectConfirm {
		connectConfirmOSCFunc = OSCFunc.new({ | msg, time, addr |
			var initiatorId = msg[1];
			if (initiatorId == targetId, {
				connectionState = \connected;
				this.onConnected.(connectionState);
			});
		},
		path: '/wireConnectConfirm',
		recvPort: receivePort
		).permanent_(true);
	}

	prBindWireSend {
	}

	prBindWireAck {
	}

	prBindWireNak {
	}

	connect {
		if (connectionState == \neverConnected, {
			connectionTask = Task.new({
				var retries = 0;

				// Send initial connection request, until response is received at /wireConnectAccept.
				connectionState = \connectionRequested;
				this.onConnected.(connectionState);

				while ({ connectionState == \connectionRequested and: { retries < retryCount }}, {
					netAddr.sendMsg('/wireConnectRequest', receivePort, targetId);
					timeOut.wait;
					retries = retries + 1;
				});

				// If we tried too many times, we did not successfully connect.
				if (retries >= retryCount, {
					connectionState = \failureTimeout;
					this.onConnected.(connectionState);
				});
			}, SystemClock).start;
		});
	}
}