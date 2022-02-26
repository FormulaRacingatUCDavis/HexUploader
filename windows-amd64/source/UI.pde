GDropList portSelect, txStyle, rxStyle;
GButton chooseButton, uploadButton, txButton, portRefresh, reconnect, resetButton;
GTextField fileBox, txBox;
GTextArea output, rx;

boolean consoleStarted = false;
boolean monitorStarted = false;

String[] txStyles = {"ASCII", "Decimal", "Hexidecimal"};
String[] rxStyles = {"Continuous", "Newline", "Null"};


void loadGUI(){
  G4P.messagesEnabled(false);
  G4P.setDisplayFont("Arial", G4P.PLAIN, 14); 
  G4P.setInputFont("Arial", G4P.PLAIN, 14);
  G4P.setGlobalColorScheme(15); 
  GButton.useRoundCorners(false);
  
  portSelect = new GDropList(this, 30, 40, 180, 90, 3, 20);
  portSelect.setItems(ports, 0);
  portSelect.insertItem(0, "Select Port");
  portSelect.setSelected(0);
  
  portRefresh = new GButton(this, 30, 75, 80, 25, "Refresh");
  reconnect = new GButton(this, 120, 75, 90, 25, "Reconnect"); 
  

  fileBox = new GTextField(this, 30, 150, 160, 25);
  fileBox.setPromptText("Select hex file");
  chooseButton = new GButton(this, 190, 151, 20, 25, ">");
  
  uploadButton = new GButton(this, 30, 185, 100, 25, "Upload");
  
  
  
  output = new GTextArea(this, 250, 40, 500, 200, G4P.SCROLLBARS_VERTICAL_ONLY);
  output.setTextEditEnabled(false);
  
  txBox = new GTextField(this, 250, 300, 400, 25);
  txBox.setPromptText("Serial Monitor");
  
  txButton = new GButton(this, 670, 300, 70, 25, "Send");
  
  txStyle = new GDropList(this, 30, 300, 180, 90, 3, 20);
  txStyle.setItems(txStyles, 0);
  
  rx = new GTextArea(this, 250, 340, 500, 200, G4P.SCROLLBARS_VERTICAL_ONLY);
  rx.setTextEditEnabled(false);
  
  //resetButton = new GButton(this, 30, 400, 100, 25, "Reset PIC18");
  //rx.setText("Hello");
  
  //rxStyle = newGDropdown
  
  
  //output.setText("Hello");
}

void handleDropListEvents(GDropList list, GEvent event) { 
  if(list == portSelect){
    USBConnect();
  } else if (list == txStyle){
    monitorMode = list.getSelectedIndex() + 1;
    if(monitorMode != 1){
      console("Please enter comma separated values");
    }
  }
}

void handleTextEvents(GEditableTextControl textField, GEvent event){
  if(event == GEvent.ENTERED){
    if(textField == txBox && serialMonitor){
      tx();
    } else if (textField == fileBox){
      hexValid = GetHexBytes(textField.getText());
    }
  }
}

void handleButtonEvents(GButton button, GEvent event){
  if(button == chooseButton){
    selectInput("Select hex file", "setFile");
  } else if (button == uploadButton && serialMonitor){     //if serialMonitor is true, system not busy
    thread("Upload");
  } else if (button == txButton && serialMonitor){
    tx();
  } else if (button == portRefresh) {
    ports = Serial.list();
    portSelect.setItems(ports, 0);
    portSelect.insertItem(0, "Select");
  } else if (button == reconnect){
    USBConnect();
  } 
}

void console(String text){
  if(consoleStarted){
    output.appendText(text);
  } else {
    output.setText(text);
    consoleStarted = true;
  }
}

void monitor(String text){
  if(monitorStarted){
    rx.appendText(text);
  } else {
    rx.setText(text);
    monitorStarted = true;
  }
}
