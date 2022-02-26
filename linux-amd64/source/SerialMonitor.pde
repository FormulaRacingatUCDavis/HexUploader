boolean serialMonitor = true;    //true when in serial monitor mode
int monitorMode = 1;             //1 = ASCII, 2 = Dec, 3 = Hex

boolean recieving = false;       //allows splitting of rx and tx printing

void tx(){
  if(!connected){
    console("Select valid port first!");
    return;
  }
  
  int[] out;
  String text = txBox.getText();
  String[] txStrings;
  
  switch (monitorMode){
      case 1: 
        txStrings = new String[1];
        txStrings[0] = text;
        out = int(text.toCharArray());
        break;
      case 2:
        text = text.replace(" ", "");
        txStrings = text.split(",");
        out = parseInt(txStrings);
        break;
      case 3:
        text = text.replace(" ", "");
        text = text.replace("0x", "");
        txStrings = text.split(",");
        out = new int[txStrings.length];
        
        for(int j = 0; j < txStrings.length; j++){
          try{
            out[j] = (unhex(txStrings[j]));              //turn two hex characters into byte of data
          } catch (Exception e) {                    //catch invalid characters
            //e.printStackTrace();
            console("Invalid value!");
            return;
          }
        }
        break;
      default: 
        return;  
    }
    
    Integer[] out2 = new Integer[out.length];
    
    for(int i = 0; i <  out.length; i++){
      if(out[i] < 256 && out[i] >= 0){
        out2[i] = out[i];
      } else {
        console("Value out of range!");
        return;
      }
    }
    
    txBox.setText("");
    
    recieving = false;
    
    monitor("  ");
    monitor("Tx:");
    monitor("--------------------------------");
    
    for(int i = 0; i < txStrings.length; i++){
       
      
      if(monitorMode == 3){
        txStrings[i] = "0x" + txStrings[i].toUpperCase();
      }
      
      monitor(txStrings[i]);
    }
    
    WriteBytes(out2);
}

String buffer = "";
boolean readyToPrintLn = false;

void rx(){
  if(serial.available() > 0){
      int inByte = serial.read();
      String out = "";
      
      switch (monitorMode){
        case 1: 
          // Did we finish reading the line?
          if(char(inByte) == '\n'){
            readyToPrintLn = true;
          }else{
            // Store line incrementally
            buffer += str(char(inByte));
          }
          break;
        case 2:
          out = str(inByte);
          break;
        case 3:
          out = hex(inByte, 2);
          break;
      }
      
      if(!recieving){
        monitor("  ");
        monitor("Rx:");
        monitor("--------------------------------");
        
        recieving = true;
      }
      if(monitorMode == 3){
        out = "0x" + out;
      }
            
      if(monitorMode == 1){
        if(readyToPrintLn){
          // Print line to monitor
          monitor(buffer);
          buffer = "";
          readyToPrintLn = false;
        }
      }else{
        monitor(out);
      }
    }
}
