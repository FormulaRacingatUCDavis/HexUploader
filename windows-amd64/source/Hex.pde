ArrayList<Integer[]> bytes;
boolean hexValid = false;

void setFile(File selection){
  if (selection == null) {
    println("Window was closed or the user hit cancel.");
  } else {
    fileBox.setText(selection.getAbsolutePath());
    hexValid = GetHexBytes(selection.getAbsolutePath());
  }
}



boolean GetHexBytes(String path){
    
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



boolean IsValidRecord(Integer[] record){
  byte result = 0;
  
  for(int i = 0; i < (record.length - 1); i++){
     result += record[i];
  }       
  result = (byte) (~ result & 0xFF);
  result = (byte) (result + 1 & 0xFF);
  
  return (result == byte(record[record.length - 1]));
}



boolean WriteRecord(Integer[] record){
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
         checksum += (~ byte(outdata[i]) & 0xFF);
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
