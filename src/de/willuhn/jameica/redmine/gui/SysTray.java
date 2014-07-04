/**********************************************************************
 *
 * Copyright (c) by Olaf Willuhn
 * All rights reserved
 *
 **********************************************************************/

package de.willuhn.jameica.redmine.gui;

import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import javax.annotation.Resource;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.Tray;
import org.eclipse.swt.widgets.TrayItem;

import com.taskadapter.redmineapi.bean.Issue;
import com.taskadapter.redmineapi.bean.TimeEntry;

import de.willuhn.jameica.gui.GUI;
import de.willuhn.jameica.gui.util.SWTUtil;
import de.willuhn.jameica.messaging.Message;
import de.willuhn.jameica.messaging.MessageConsumer;
import de.willuhn.jameica.messaging.QueryMessage;
import de.willuhn.jameica.messaging.StatusBarMessage;
import de.willuhn.jameica.redmine.Plugin;
import de.willuhn.jameica.redmine.beans.ProjectTree;
import de.willuhn.jameica.redmine.service.RedmineService;
import de.willuhn.jameica.redmine.util.DurationFormatter;
import de.willuhn.jameica.system.Application;
import de.willuhn.jameica.system.OperationCanceledException;
import de.willuhn.jameica.system.Settings;
import de.willuhn.logging.Logger;
import de.willuhn.util.ApplicationException;
import de.willuhn.util.I18N;

/**
 * Zeigt das Systray an.
 */
public class SysTray
{
  private final static I18N i18n = Application.getPluginLoader().getPlugin(Plugin.class).getResources().getI18N();
  
  @Resource
  private RedmineService service;
  
  private final static DurationFormatter format = new DurationFormatter();
  private static TrayItem item = null;

  /**
   * Startet das Systray.
   */
  public void start()
  {
    if (item != null)
      return;
    
    Tray sysTray = GUI.getDisplay().getSystemTray();
    if (sysTray == null)
      return;
    
    Application.getMessagingFactory().registerMessageConsumer(new TaskChangedMessageConsumer());
    
    item = new TrayItem(sysTray,SWT.NONE);
    item.addListener(SWT.FocusIn,new Listener()
    {
      public void handleEvent(Event event)
      {
        refresh();
      }
    
    });

    // Menu aufklappen bei Klick mit der rechten Maustaste
    item.addListener (SWT.MenuDetect, new Listener()
    {
      public void handleEvent(Event event)
      {
        getMenu();
      }
    });
    
    // Minimieren/Maximieren beim Klick mit der linken Maustaste.
    item.addSelectionListener(new SelectionAdapter() {
    
      public void widgetSelected(SelectionEvent e)
      {
        GUI.getShell().setMinimized(!GUI.getShell().getMinimized());
      }
    });
    refresh();
    
    // 1 mal pro Minute refresh aufrufen, um die Uhrzeit zu aktualisieren
    Timer timer = new Timer(true);
    TimerTask task = new TimerTask()
    {
    
      public void run()
      {
        refresh();
      }
    };
    timer.schedule(task,60 * 1000L,60 * 1000L);
  }

  /**
   * Stoppt das Systray.
   */
  public void stop()
  {
    
  }
  

  /**
   * Oeffnet das ContextMenu.
   */
  private void getMenu()
  {
    try
    {
      final TimeEntry current = null; //TODO
      
      final Menu menu = new Menu(GUI.getShell(), SWT.POP_UP);

      ///////////////////////////////////////////////////////////////
      // Programm beenden
      MenuItem shutdown = new MenuItem(menu, SWT.PUSH);
      shutdown.setText("Programm beenden");
      shutdown.addListener(SWT.Selection, new Listener()
      {
        public void handleEvent (Event e)
        {
          try
          {
            if (Application.getCallback().askUser(i18n.tr("Jameica wirklich beenden?")))
              System.exit(0);
          }
          catch (OperationCanceledException oce)
          {
            // ignore
          }
          catch (Exception e2)
          {
            Logger.error("unable to shutdown",e2);
          }
        }
      });
      ///////////////////////////////////////////////////////////////

      new MenuItem(menu, SWT.SEPARATOR);
      ///////////////////////////////////////////////////////////////
      // Liste der verfuegbaren Tasks
      
      List<ProjectTree> projects = service.getProjects();
      
      for (ProjectTree p:projects)
      {
        createMenu(p,menu);
      }
      ///////////////////////////////////////////////////////////////
      
      ///////////////////////////////////////////////////////////////
      // aktuellen Job anhalten
      if (current != null)
      {
        new MenuItem(menu, SWT.SEPARATOR);
        MenuItem stop = new MenuItem(menu, SWT.PUSH);
//        stop.setText(i18n.tr("Aufgabe anhalten: [{0}] {1}",new String[]{format.format(new Integer(current.getDuration())),current.getName()}));
        stop.setText(i18n.tr("Aufgabe anhalten"));
        stop.addListener(SWT.Selection, new Listener()
        {
          public void handleEvent (Event e)
          {
            stopJob(current);
            menu.setVisible(false);
            menu.dispose();
          }
        });
      }
      ///////////////////////////////////////////////////////////////
      
      // Menu anzeigen
      menu.setVisible(true);
    }
    catch (Exception e)
    {
      Logger.error("unable to display menu",e);
    }
  }
  
  /**
   * Erzeugt rekursiv das Menu fuer das Projekt.
   * @param p das Projekt.
   * @param menu das Hauptmenu.
   * @throws ApplicationException
   */
  private void createMenu(ProjectTree p, Menu menu) throws ApplicationException
  {
    List<ProjectTree> children = p.getChildren();
    List<Issue> issues = service.getIssues(p.getProject());
    
    // Das Projekt ist leer. Nicht anzeigen
    if (issues.size() == 0 && children.size() == 0)
      return;

    MenuItem pm = new MenuItem(menu,SWT.CASCADE);
    pm.setData(p);
    pm.setText(p.getProject().getName());
    Menu sub = new Menu(menu);
    pm.setMenu(sub);

    // Erst die Kind-Projekte.
    for (ProjectTree c:children)
    {
      this.createMenu(c,sub);
    }
    
    // Jetzt die Issues.
    for (Issue i:issues)
    {
      final MenuItem mi = new MenuItem(sub,SWT.PUSH);
      mi.setData(i);
      mi.setText("#" + i.getId() + " " + i.getSubject());
//      mi.addListener(SWT.Selection, new Listener()
//      {
//        public void handleEvent (Event e)
//        {
          // Erst den alten Job ggf. stoppen
//          if (stopJob(current))
//          {
            // Jetzt einen neuen Job anlegen
//              try
//              {
              // TODO Job starten
              // new JobStart().handleAction(current);
//              refresh();
//              }
//              catch (ApplicationException ae)
//              {
//                Application.getMessagingFactory().sendMessage(new StatusBarMessage(ae.getMessage(),StatusBarMessage.TYPE_ERROR));
//              }
//          }
//          menu.setVisible(false);
//          menu.dispose();
//        }
//      });
    }
  }

  /**
   * Stoppt den uebergebenen Task.
   * @param current der zu stoppende Job.
   * @return true, wenn der Job gestoppt wurde.
   */
  private boolean stopJob(TimeEntry current)
  {
    try
    {
      if (current != null)
      {
        Settings settings = Application.getPluginLoader().getPlugin(Plugin.class).getResources().getSettings();
        if (settings.getBoolean("job.enterdescription",true))
        {
          String text = current.getComment();
          if (text == null || text.length() == 0)
          {
            // text = Application.getCallback().askUser(i18n.tr("Bitte geben Sie einen Kommentar ein.\nAufgabe: [{0}] {1}", new String[]{format.format(new Integer(current.getDuration())),current.getName()}),i18n.tr("Kommentar"));
            text = Application.getCallback().askUser(i18n.tr("Bitte geben Sie einen Kommentar ein."),i18n.tr("Kommentar"));
          }
          current.setComment(text);
        }
        
        // TODO: Job stoppen
        // new JobStop().handleAction(current);
      }
      return true;
    }
    catch (OperationCanceledException oce)
    {
      // ignore
    }
    catch (ApplicationException ae)
    {
      Application.getMessagingFactory().sendMessage(new StatusBarMessage(ae.getMessage(),StatusBarMessage.TYPE_ERROR));
    }
    catch (Exception e)
    {
      Logger.error("unable to stop job",e);
      Application.getMessagingFactory().sendMessage(new StatusBarMessage(i18n.tr("Fehler beim Stoppen der Zeiterfassung"),StatusBarMessage.TYPE_ERROR));
    }
    return false;
  }
  
  /**
   * Aktualisiert das Icon des Systray.
   */
  private void refresh()
  {
    GUI.getDisplay().asyncExec(new Runnable() {
      
      public void run()
      {
        try
        {
//          User user = SystemMessageConsumer.getCurrentUser();
          ClassLoader cl = Application.getPluginLoader().getManifest(Plugin.class).getClassLoader();
//          Job currentJob = user.getCurrentJob();
//          if (currentJob != null)
//          {
//            item.setToolTipText(i18n.tr("[{0}] {1}",new String[]{format.format(new Integer(currentJob.getDuration())),currentJob.getName()}));
            item.setToolTipText("foo");
            item.setImage(SWTUtil.getImage("work.png",cl));
//          }
//          else
//          {
//            item.setToolTipText("Keine laufende Aufgabe");
//            item.setImage(SWTUtil.getImage("clock.png",cl));
//          }
        }
        catch (Exception e)
        {
          Logger.error("unable to refresh icon",e);
        }
      }
    
    });
  }

  
  /**
   * Message-Consumer zum aktualisieren des Icons.
   */
  private class TaskChangedMessageConsumer implements MessageConsumer
  {

    /**
     * @see de.willuhn.jameica.messaging.MessageConsumer#autoRegister()
     */
    public boolean autoRegister()
    {
      return false;
    }

    /**
     * @see de.willuhn.jameica.messaging.MessageConsumer#getExpectedMessageTypes()
     */
    public Class[] getExpectedMessageTypes()
    {
      // Fuer Updates aus anderen Stellen heraus
      return new Class[]{QueryMessage.class};
    }

    /**
     * @see de.willuhn.jameica.messaging.MessageConsumer#handleMessage(de.willuhn.jameica.messaging.Message)
     */
    public void handleMessage(Message message) throws Exception
    {
      if (message == null)
        return;
      refresh();
    }
    
  }
  
}

