import processing.core.*; 
import processing.data.*; 
import processing.event.*; 
import processing.opengl.*; 

import g4p_controls.*; 
import processing.serial.*; 

import java.util.HashMap; 
import java.util.ArrayList; 
import java.io.File; 
import java.io.BufferedReader; 
import java.io.PrintWriter; 
import java.io.InputStream; 
import java.io.OutputStream; 
import java.io.IOException; 

public class HexUploader extends PApplet {

/*

03/12/2021

Hex uploader & serial monitor designed for PICDuino/Austuino UNO


Leo Janzen


*/





String port;
Serial serial;// = new Serial(this, "COM8", 9600);

int error;


boolean connected = false;
boolean upload = false;


//StringList consoleLog = new StringList();

int index = 0;

String[] ports = Serial.list();


public void setup() {
  
  
  loadGUI();
  
  

}



public void draw() {
  //println(serialMonitor);
  background(240); 

  if(serialMonitor && connected){
    rx();
  }
}

public void USBConnect(){
    if(portSelect.getSelectedIndex() == 0){
      serial.stop();
      connected = false;
      return;
    }
    
    port = ports[portSelect.getSelectedIndex() - 1];
    
    try{
      
      if(connected){
        serial.stop();
      }
      
      serial = new Serial(this, port, 9600);
      connected = true;
      
      if(hexValid){
        uploadButton.setLocalColorScheme(1);    //turn button green
      }
      
      if(Handshake()){
        console("Austuino found on " + port);
      } else {
        console("YIKES! Device may not be an Austuino!");
      }
      
      portSelect.setLocalColorScheme(15); 
      
    } catch (Exception e) {
      console("Could not connect to " + port);
      portSelect.setLocalColorScheme(0);
      connected = false;
    }
}

public void Upload(){
  UploadInner();
  serialMonitor = true;      //
}

public void UploadInner(){
  
  if(!connected){
    console("Select valid port first!");
    return;
  }
  
  if(!hexValid){
    console("Select valid hex file first!");
    return;
  }
  
  uploadButton.setLocalColorScheme(15);
  
  serialMonitor = false;               //disable serial monitor
  serial.clear();                      //clear any available bytes
  
  console("Connecting...");
  
  if(!Connect()){ return; }
   
  console("Erasing flash...");
  
  if(!EraseFlash()){ return; }
  
  delay(10);
  
  console("Uploading...");
  
  checksum = 0;

  for(int i = 0; i < bytes.size(); i++){
    if(!WriteRecord(bytes.get(i))) { return; }
    //delay(10);
  }
  
  checksum %= 65536;   //convert to 16 bits
  
  console("Verifying...");
  
  boolean checksumComplete = CalculateChecksum();
  
  if(checksumComplete && (receivedChecksum == checksum)){
    console("Success!");
  } else {
    console("Verification failed!");
  }
  
  Reset();
  
  serialMonitor = true;
}
Integer[] data = {0};
Integer[] echo = new Integer[9];

Integer[] readBytes;

Integer[] GET_VERSION = {0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
Integer[] WRITE_FLASH = {0x02, 0x00, 0x00, 0x55, 0xAA, 0x00, 0x00, 0x00, 0x00};
Integer[] ERASE_FLASH = {0x03, 0x00, 0x00, 0x55, 0xAA, 0x00, 0x00, 0x00, 0x00};
Integer[] CALCULATE_CHECKSUM = {0x08, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};

int RESET_VECTOR = 0x0900;
int FLASH_SIZE = 0x010000;
int ERASE_ROW_SIZE = 0x80;
int TOTAL_FLASH_ROWS = (FLASH_SIZE - RESET_VECTOR)/ERASE_ROW_SIZE;
int TOTAL_BYTES = FLASH_SIZE - RESET_VECTOR;

int checksum = 0;
int receivedChecksum = 0;

//Integer[] dat = {0xFE, 0xEF, 0x7F, 0xF0};




//attempt connection with USB/UART translator
public boolean Handshake(){
  for(int i = 0; i < 10; i++){ 
    serial.write(0x05);
    delay(10);
    serial.write(0xAA);
    delay(10);
    
    if(serial.available() > 0){
      return (serial.read() == 0x99);
    }
    
    delay(100);
  }

  return false;
}
  
  
//attempt connection with bootloader
public boolean Connect(){
  
  for(int i = 0; i < 10; i++){
    Reset();
    delay(100);
    
    serial.write(0x55);
    delay(50);
    
    if(serial.available() > 0){
      if(serial.read() == 0x69){
        return true;
      }
    }
    
    delay(100);
  }
  
  console("Connection failed");
  
  return false;
}


public boolean Reset(){                  //send command to reset PIC18
  serial.write(0x05);
  delay(10);
  serial.write(0xBB);
  delay(20);
  
  if(!ReadBytes(1)){ return false; }
  
  //return true;
  return CompareResult(readBytes[0], 1);
}




public boolean GetVersion(){            
  return BootloaderSendRecieve(GET_VERSION, 16);
}



public boolean EraseFlash(){
  
  int[] rows = Int2TwoBytes(TOTAL_FLASH_ROWS);
  
  ERASE_FLASH[1] = rows[1];
  ERASE_FLASH[2] = rows[0];
  
  int[] address = Int2TwoBytes(RESET_VECTOR);
  
  ERASE_FLASH[5] = address[1];
  ERASE_FLASH[6] = address[0];
  
  if(!BootloaderSendRecieve(ERASE_FLASH, 1)) { return false; }
  
  return CompareResult(data[0], 1);
}




public boolean WriteFlash(Integer[] outdata, int startAddress){
  
  int[] lengthBytes = Int2TwoBytes(outdata.length);             //convert length into bytes
  
  WRITE_FLASH[1] = lengthBytes[1];                                //little endian
  WRITE_FLASH[2] = lengthBytes[0];
  
  if(startAddress == 0 && WRITE_FLASH[7] == 0 && WRITE_FLASH[8] == 0){    //map reset vector to new reset vector;
    startAddress = RESET_VECTOR;
  }
  
  int[] addressBytes = Int2TwoBytes(startAddress);    
  
  WRITE_FLASH[5] = addressBytes[1];               //little endian
  WRITE_FLASH[6] = addressBytes[0];               //upper + extended bytes set when reading address records
  
  if(!BootloaderSendRecieve(WRITE_FLASH, outdata, 1)) { return false; }
  
  return CompareResult(data[0], 1);
}


//calculate 16 bit checksum from reset vector to end of flash
//No ops (0xffff) are ignored because each byte is bitwise not-ed
public boolean CalculateChecksum(){
  int[] numBytes = Int2TwoBytes(TOTAL_BYTES);
  
  CALCULATE_CHECKSUM[1] = numBytes[1];
  CALCULATE_CHECKSUM[2] = numBytes[0];
  
  int[] resetVector = Int2TwoBytes(RESET_VECTOR);
  
  CALCULATE_CHECKSUM[5] = resetVector[1];
  CALCULATE_CHECKSUM[6] = resetVector[0];
  
  if(!BootloaderSendRecieve(CALCULATE_CHECKSUM, 2)) { return false; }
  
  receivedChecksum = TwoBytes2Int(data[1], data[0]);
  
  return true;
}
  


public int[] Int2TwoBytes(int val){         //convert int into low and high bytes
  int H = floor(val / 256);
  int L = val % 256;
  int[] out = {H, L};
  return out;
}

public int TwoBytes2Int(int H, int L){     //convert high and low bytes to int
  return 256*H + L;
}

  
public boolean CompareResult(int result, int expected){
  if(result != expected){
    console("Unexpected response: 0x" + hex(result, 2));
    return false;
  }
  
  return true;
}

public boolean BootloaderSendRecieve(Integer[] command, Integer[] outData, int inLen){
  
  serial.clear();
  
  WriteBytes(command);
  
  WriteBytes(outData);
  
  if(!ReadBytes(9)){return false;}
  
  echo = readBytes;
      
  if(!ReadBytes(inLen)){return false;}
  
  data = readBytes;
  
  return true;
}

public boolean BootloaderSendRecieve(Integer[] command, int inLen){
  serial.clear();
  
  WriteBytes(command);
  
  if(!ReadBytes(9)){ return false; }
  
  echo = readBytes;
      
  if(!ReadBytes(inLen)){ return false; }
  
  data = readBytes;
  
  return true;
}


public void  WriteBytes(Integer[] bytes){
  for(int i = 0; i < bytes.length; i++){
    
    if(bytes[i] == 0x05){                //if byte is escape character, send twice
      serial.write(0x05);     
    }
    
    serial.write(bytes[i]);
  }
}


public boolean ReadBytes(int len){
  readBytes  = new Integer[len];
  int tries = 0;
  
  for(int i = 0; i < len; i++){
    if(serial.available() > 0){
      readBytes[i] = serial.read();
    } else {
      if(tries > 1000){
        console("ERROR: No response!");
        return false;
      }
      i--;
      tries++;
      delay(1);
    }  
  }  
  
  return true;
}
ArrayList<Integer[]> bytes;
boolean hexValid = false;

public void setFile(File selection){
  if (selection == null) {
    println("Window was closed or the user hit cancel.");
  } else {
    fileBox.setText(selection.getAbsolutePath());
    hexValid = GetHexBytes(selection.getAbsolutePath());
  }
}



public boolean GetHexBytes(String path){
    
    bytes = new ArrayList<Integer[]>();
    
    BufferedReader reader = createReader(path);
    
    String line = null;
    
    int total = 0;
      
    try {
      while ((line = reader.readLine()) != null) {
        String[] lineChars = str(line.toCharArray());
        
        if (lineChars[0].equals(":")){   //make sure line starts with record start character
          int numBytes  = (lineChars.length - 1)/2;
          
          Integer[] temp = new Integer[numBytes];
          
          for(int j = 0; j < numBytes; j++){
            temp[j] = (unhex(lineChars[2*j + 1] + lineChars[2*j + 2]));     //turn two hex characters into byte of data
          }
          
          total++;
          
          if (IsValidRecord(temp)){
            bytes.add(temp); 
          } else {
            console("Invalid record on line " + str(total));
            fileBox.setLocalColorScheme(0);
            chooseButton.setLocalColorScheme(0);
            return false;
          }
        }
      }    
    } catch (Exception e) {
      e.printStackTrace();
      console("Invalid file");
      fileBox.setLocalColorScheme(0);
      chooseButton.setLocalColorScheme(0);
      return false;
    } 
    
    if(total == 0){
      console("Invalid file");
      fileBox.setLocalColorScheme(0);
      chooseButton.setLocalColorScheme(0);
      return false;
    }
      
    console("Hex file loaded");
    
    if(connected){
      uploadButton.setLocalColorScheme(1);    //turn button green
    }
    
    fileBox.setLocalColorScheme(15);
    chooseButton.setLocalColorScheme(15);
    
    return true;
}



public boolean IsValidRecord(Integer[] record){
  byte result = 0;
  
  for(int i = 0; i < (record.length - 1); i++){
     result += record[i];
  }       
  result = (byte) (~ result & 0xFF);
  result = (byte) (result + 1 & 0xFF);
  
  return (result == PApplet.parseByte(record[record.length - 1]));
}



public boolean WriteRecord(Integer[] record){
   int command = record[3];
   
   if(command == 0x00){           //data record
     int startAddress = TwoBytes2Int(record[1], record[2]);
     int dataLength = record[0];
     Integer[] outdata = new Integer[dataLength];
     
     int fullAddress = 65536*TwoBytes2Int(WRITE_FLASH[8], WRITE_FLASH[7]) + startAddress;
     boolean programMemory = fullAddress < FLASH_SIZE;
     
     for (int i = 0; i < dataLength; i++){
       outdata[i] = record[i + 4];
       
       
       if(programMemory){         
         checksum += (~ PApplet.parseByte(outdata[i]) & 0xFF);
       }
     }
     
     if(programMemory){
       WRITE_FLASH[0] = 0x02;     //write flash command
     } else {
       WRITE_FLASH[0] = 0x07;     //write config command
     }
     
     return WriteFlash(outdata, startAddress);
     
   } else if(command == 0x04) {    //extended linear address record
     WRITE_FLASH[7] = record[5];
     WRITE_FLASH[8] = record[4];
     
     return true;
     
   } else if (command == 0x01) {   //EOF 
     return true;
   }
   
   console("Unsupported Record Type");
   return false;
}
boolean serialMonitor = true;    //true when in serial monitor mode
int monitorMode = 1;             //1 = ASCII, 2 = Dec, 3 = Hex

boolean recieving = false;       //allows splitting of rx and tx printing

public void tx(){
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
        out = PApplet.parseInt(text.toCharArray());
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

public void rx(){
  if(serial.available() > 0){
      int inByte = serial.read();
      String out = "";
      
      switch (monitorMode){
        case 1: 
          out = str(PApplet.parseChar(inByte));
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
      
      monitor(out);
    }
}
GDropList portSelect, txStyle, rxStyle;
GButton chooseButton, uploadButton, txButton, portRefresh, reconnect, resetButton;
GTextField fileBox, txBox;
GTextArea output, rx;

boolean consoleStarted = false;
boolean monitorStarted = false;

String[] txStyles = {"ASCII", "Decimal", "Hexidecimal"};
String[] rxStyles = {"Continuous", "Newline", "Null"};


public void loadGUI(){
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

public void handleDropListEvents(GDropList list, GEvent event) { 
  if(list == portSelect){
    USBConnect();
  } else if (list == txStyle){
    monitorMode = list.getSelectedIndex() + 1;
    if(monitorMode != 1){
      console("Please enter comma separated values");
    }
  }
}

public void handleTextEvents(GEditableTextControl textField, GEvent event){
  if(event == GEvent.ENTERED){
    if(textField == txBox && serialMonitor){
      tx();
    } else if (textField == fileBox){
      hexValid = GetHexBytes(textField.getText());
    }
  }
}

public void handleButtonEvents(GButton button, GEvent event){
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

public void console(String text){
  if(consoleStarted){
    output.appendText(text);
  } else {
    output.setText(text);
    consoleStarted = true;
  }
}

public void monitor(String text){
  if(monitorStarted){
    rx.appendText(text);
  } else {
    rx.setText(text);
    monitorStarted = true;
  }
}
  public void settings() {  size(800, 600); }
  static public void main(String[] passedArgs) {
    String[] appletArgs = new String[] { "HexUploader" };
    if (passedArgs != null) {
      PApplet.main(concat(appletArgs, passedArgs));
    } else {
      PApplet.main(appletArgs);
    }
  }
}
