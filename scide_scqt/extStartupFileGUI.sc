+ StartupFile {

	*select {
		var w, selectButtons;
		var onCol = Color.green;

		this.updateFiles;

		w = Window.new(
			name: "Select startup file:",
			bounds: Rect.aboutPoint(Window.screenBounds.center, 100, 100)
		);


		selectButtons = this.fileNames.collect { |name|
			var col = if (name == currentName, onCol);
			Button.new(parent: w)
			.states_([[name, nil, col]])
			.action_({
				this.writeRedirectFile(name);
				currentName = name;
				w.close
			})
		};

		w.layout = VLayout(*selectButtons);
		w.front;
	}
}