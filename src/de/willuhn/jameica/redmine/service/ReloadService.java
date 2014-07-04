/**********************************************************************
 *
 * Copyright (c) by Olaf Willuhn
 * All rights reserved
 *
 **********************************************************************/

package de.willuhn.jameica.redmine.service;

import java.rmi.RemoteException;
import java.util.Timer;
import java.util.TimerTask;

import javax.annotation.Resource;

import de.willuhn.datasource.Service;
import de.willuhn.jameica.redmine.Settings;
import de.willuhn.logging.Logger;

/**
 * Service, der den Cache regelmaessig neu laedt.
 */
public class ReloadService implements Service
{
  @Resource
  private CachingRedmineService service;
  
  @Resource
  private Settings settings;
  
  private Timer timer = null;
  private Task task = null;
  
  /**
   * Startet den Scheduler mit ggf geaenderten Intervall neu.
   * @throws RemoteException
   */
  public void reschedule() throws RemoteException
  {
    if (!this.isStarted())
    {
      Logger.warn("reload-service not started, skipping re-schedule");
    }
    
    if (this.task != null)
    {
      Logger.info("cancel current task");
      this.task.cancel();
    }
    
    int minutes = this.settings.getCacheReloadInterval();
    Logger.info("reloading cache every " + minutes + " minutes");
    this.task = new Task();
    this.timer.schedule(this.task,4 * 1000L,minutes * 60 * 1000L);
  }
  
  /**
   * @see de.willuhn.datasource.Service#getName()
   */
  @Override
  public String getName() throws RemoteException
  {
    return "reload-service";
  }

  /**
   * @see de.willuhn.datasource.Service#isStartable()
   */
  @Override
  public boolean isStartable() throws RemoteException
  {
    return !this.isStarted();
  }

  /**
   * @see de.willuhn.datasource.Service#isStarted()
   */
  @Override
  public boolean isStarted() throws RemoteException
  {
    return this.timer != null;
  }

  /**
   * @see de.willuhn.datasource.Service#start()
   */
  @Override
  public void start() throws RemoteException
  {
    this.timer = new Timer(true);
    this.reschedule();
  }
  
  /**
   * @see de.willuhn.datasource.Service#stop(boolean)
   */
  @Override
  public void stop(boolean arg0) throws RemoteException
  {
    try
    {
      if (this.task != null)
      {
        this.task.cancel();
      }
      
      if (this.timer != null)
      {
        this.timer.cancel();
      }
    }
    catch (Exception e)
    {
      Logger.error("error while shutting down service",e);
    }
    finally
    {
      this.task = null;
      this.timer = null;
    }
  }
  
  /**
   * Unser Timer-Task.
   */
  private class Task extends TimerTask
  {
    /**
     * @see java.util.TimerTask#run()
     */
    @Override
    public void run()
    {
      service.reconnect();
    }
  }
  

}


