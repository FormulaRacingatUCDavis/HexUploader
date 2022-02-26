/*

03/12/2021

Hex uploader & serial monitor designed for PICDuino/Austuino UNO


Leo Janzen


*/


import g4p_controls.*;
import processing.serial.*;

String port;
Serial serial;// = new Serial(this, "COM8", 9600);

int error;


boolean connected = false;
boolean upload = false;


//StringList consoleLog = new StringList();

int index = 0;

String[] ports = Serial.list();


void setup() {
  size(800, 600);
  
  loadGUI();
  
  

}



void draw() {
  //println(serialMonitor);
  background(240); 

  if(serialMonitor && connected){
    rx();
  }
}

void USBConnect(){
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

void Upload(){
  UploadInner();
  serialMonitor = true;      //
}

void UploadInner(){
  
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
