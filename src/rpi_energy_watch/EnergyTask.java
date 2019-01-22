/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package rpi_energy_watch;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;
import rpio_client.Net_RPI_IO;

/**
 *
 * @author Federico
 */
public class EnergyTask {
    
    private static final int MAINS = 6; //Input port on RPI
    private static final int GENERATOR = 7; //Input port on RPI
    private static final int TVSS = 8; //Input port on RPI
    private static final int GEN_RUNNING = 1; //Output relay on RPI
    public static final int ENERGYTASK = 2;
    public static final int TASKLEVEL = 1;
    
    Net_RPI_IO rpio = null;
    private String address = "";
    private boolean runFlag = false;
    
    private int mainState=0;
    private int genState=0;
    private int surgeState=0;
    
    private Date date_period=null;
    private long mains_start_time=0;
    private long mains_down_time=0;
    private int mains_loss_counter=0;
    
    private long gen_start_time=0;
    private long gen_alive_time=0;
    private int gen_start_counter=0;
    
    public EnergyTask(String address){
        this.address=address;
        this.rpio = new Net_RPI_IO(this.address,30000);
        date_period=new Date();
    }
    
    public EnergyTask(){
        this.address="localhost";
        this.rpio = new Net_RPI_IO(this.address,30000);
        date_period=new Date();
    }
    public void start(){
        runFlag=true;
        Thread energy = new Thread(new task(),"Energy Task");
        energy.start();
    }
    
    public String getStatus(){
        return "status";
    }
    
    public String killThread(){
        runFlag=false;
        return "Killed";
    }
    public String reset_power() {
        date_period=new Date();
        mains_start_time=System.currentTimeMillis();
        mains_down_time=0;
        mains_loss_counter=0;
        gen_alive_time=0;
        gen_start_counter=0;
        
        String resp="Reset command executed.";
        return resp;
    }
    
    public String getReport(){
        String report="";
        SimpleDateFormat ft = 
        new SimpleDateFormat ("dd/MM/yyyy  HH:mm");
        Date date=new Date();
        
        if(mainState==0)
            report=report+"Main Power: ON\n";
        else
            report=report+"Main Power: OFF\n";
              
        if(genState==0)
            report=report+"Generator: STOP\n";
        else
            report=report+"Generator: RUNNING\n";
        
        if(surgeState==0)
            report=report+"Surge Protection: ACTIVE\n\n";
        else
            report=report+"Surge Protection: DISCONNECTED\n\n";
        
        String mains=String.format("%.1f", mains_down_time/(1000.0*3600.0));
        String gen=String.format("%.1f",gen_alive_time/(1000.0*3600.0));
        
        report=report+"Period: "+ft.format(date_period)+" to "+ft.format(date);
        report=report+"\nMain Power OFF Time: "+mains+" hrs\n";
        report=report+"Main Power Interrupt counter: "+mains_loss_counter+"\n\n";
        report=report+"Generator RUNNING time: "+gen+" hrs\n";
        report=report+"Generator starts counter: "+gen_start_counter+"\n\n";
        
        return report;
    }
    
    public String getLogEvents(){
        String file="/home/pi/NetBeansProjects/RPI_Energy_Watch/power_log.txt";
        return file;
    }
    
    private void logEvent(String event) throws IOException{
        
        //Check if file exists
        File f=new File("/home/pi/NetBeansProjects/RPI_Energy_Watch/power_log.txt");
     //   File f=new File("power_log.txt");
        if(!f.exists()){
            f.createNewFile(); //Create file
        } 
        
        try (FileWriter fw = new FileWriter("/home/pi/NetBeansProjects/RPI_Energy_Watch/power_log.txt",true)) {
      //  try (FileWriter fw = new FileWriter("power_log.txt",true)) {
            PrintWriter pw=new PrintWriter(fw);
            pw.println(event);
            pw.close();
        }
        
    }
    public String getDate() {
        Date dNow = new Date();
        SimpleDateFormat ft
                = new SimpleDateFormat("dd/MM/yyyy 'at' HH:mm:ss");
        String actualDate=ft.format(dNow);
        return actualDate;
    }
    
    private boolean get_input(int in){
        String resp = rpio.getInput(ENERGYTASK, TASKLEVEL, in);
        String parts[] = resp.split(",");
        boolean status;
        if(parts.length==3){
            status=Boolean.parseBoolean(parts[2]);
        } else {
            status = true;
        }
        return status;
    }
    public class task implements Runnable{

        @Override
        public void run() {
            
            rpio.setLock(ENERGYTASK, TASKLEVEL, GEN_RUNNING);
             while (runFlag) {

                try {
                    checkMains();
                    checkGen();
                    checkSurge();
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ex) {
                        Logger.getLogger(task.class.getName()).log(Level.SEVERE, null, ex);
                    }
                } catch (IOException ex) {
                    Logger.getLogger(EnergyTask.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
            rpio.releaseLock(ENERGYTASK, TASKLEVEL, GEN_RUNNING);
        }
    
    }
    private void checkMains() throws IOException{
        
        switch(mainState){
            case 0:
                if(!get_input(MAINS)){
                    mainState=1;
                    mains_start_time=System.currentTimeMillis();
                    mains_loss_counter++;
                    String message=getDate();
                    message=message+" Mains Power OFF";
                    logEvent(message);
                    System.out.println(message);
                }
                break;
                
            case 1:
                if(get_input(MAINS)){
                    mainState=0;
                    String message=getDate();
                    message=message+" Mains Power ON";
                    logEvent(message);
                    System.out.println(message);
                }
                mains_down_time=mains_down_time+(System.currentTimeMillis()-mains_start_time);
                mains_start_time=System.currentTimeMillis();
                break;
            default:
        }
    
    }
    
    private void checkGen() throws IOException {
        switch (genState) {
            case 0:
                if (get_input(GENERATOR)) {
                    genState = 1;
                    gen_start_time=System.currentTimeMillis();
                    gen_start_counter++;
                    String message = getDate();
                    message = message + " Generator ON";
                    logEvent(message);
                    rpio.setRly(ENERGYTASK, TASKLEVEL, GEN_RUNNING);
                    System.out.println(message);
                }
                break;

            case 1:
                if (!get_input(GENERATOR)) {
                    genState = 0;
                    
                    String message = getDate();
                    message = message + " Generator OFF";
                    System.out.println(message);
                    logEvent(message);
                    rpio.resetRly(ENERGYTASK, TASKLEVEL, GEN_RUNNING);
                }
                gen_alive_time=gen_alive_time+(System.currentTimeMillis()-gen_start_time);
                gen_start_time=System.currentTimeMillis();
                break;
            default:
        }
    }
    
     private void checkSurge(){
    switch (surgeState) {
            case 0:
                if (get_input(TVSS)) {
                    surgeState = 1;
                    String message = "";
                    message = message + "\nSurge Protector FAIL.\nSurge Protection INOPERATIVE";
                    System.out.println(message);
                    
                }
                break;

            case 1:
                if (!get_input(TVSS)) {
                    surgeState = 0;
                    String message = "";
                    message = message + "\nSurge Protection ACTIVE";
                    System.out.println(message);
                }
                break;
            default:
        }
    }
    
}
