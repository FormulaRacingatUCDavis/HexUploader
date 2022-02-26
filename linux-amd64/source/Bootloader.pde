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
boolean Handshake(){
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
boolean Connect(){
  
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


boolean Reset(){                  //send command to reset PIC18
  serial.write(0x05);
  delay(10);
  serial.write(0xBB);
  delay(20);
  
  if(!ReadBytes(1)){ return false; }
  
  //return true;
  return CompareResult(readBytes[0], 1);
}




boolean GetVersion(){            
  return BootloaderSendRecieve(GET_VERSION, 16);
}



boolean EraseFlash(){
  
  int[] rows = Int2TwoBytes(TOTAL_FLASH_ROWS);
  
  ERASE_FLASH[1] = rows[1];
  ERASE_FLASH[2] = rows[0];
  
  int[] address = Int2TwoBytes(RESET_VECTOR);
  
  ERASE_FLASH[5] = address[1];
  ERASE_FLASH[6] = address[0];
  
  if(!BootloaderSendRecieve(ERASE_FLASH, 1)) { return false; }
  
  return CompareResult(data[0], 1);
}




boolean WriteFlash(Integer[] outdata, int startAddress){
  
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
boolean CalculateChecksum(){
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
  


int[] Int2TwoBytes(int val){         //convert int into low and high bytes
  int H = floor(val / 256);
  int L = val % 256;
  int[] out = {H, L};
  return out;
}

int TwoBytes2Int(int H, int L){     //convert high and low bytes to int
  return 256*H + L;
}

  
boolean CompareResult(int result, int expected){
  if(result != expected){
    console("Unexpected response: 0x" + hex(result, 2));
    return false;
  }
  
  return true;
}

boolean BootloaderSendRecieve(Integer[] command, Integer[] outData, int inLen){
  
  serial.clear();
  
  WriteBytes(command);
  
  WriteBytes(outData);
  
  if(!ReadBytes(9)){return false;}
  
  echo = readBytes;
      
  if(!ReadBytes(inLen)){return false;}
  
  data = readBytes;
  
  return true;
}

boolean BootloaderSendRecieve(Integer[] command, int inLen){
  serial.clear();
  
  WriteBytes(command);
  
  if(!ReadBytes(9)){ return false; }
  
  echo = readBytes;
      
  if(!ReadBytes(inLen)){ return false; }
  
  data = readBytes;
  
  return true;
}


void  WriteBytes(Integer[] bytes){
  for(int i = 0; i < bytes.length; i++){
    
    if(bytes[i] == 0x05){                //if byte is escape character, send twice
      serial.write(0x05);     
    }
    
    serial.write(bytes[i]);
  }
}


boolean ReadBytes(int len){
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
