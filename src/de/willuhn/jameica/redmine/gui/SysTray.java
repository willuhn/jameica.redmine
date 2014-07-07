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

import de.willuhn.annotation.Lifecycle;
import de.willuhn.annotation.Lifecycle.Type;
import de.willuhn.jameica.gui.GUI;
import de.willuhn.jameica.gui.internal.action.FileClose;
import de.willuhn.jameica.gui.util.SWTUtil;
import de.willuhn.jameica.messaging.Message;
import de.willuhn.jameica.messaging.MessageConsumer;
import de.willuhn.jameica.messaging.QueryMessage;
import de.willuhn.jameica.messaging.StatusBarMessage;
import de.willuhn.jameica.redmine.DismissTimeEntryException;
import de.willuhn.jameica.redmine.Plugin;
import de.willuhn.jameica.redmine.beans.ProjectTree;
import de.willuhn.jameica.redmine.gui.dialogs.TimeEntryCommitDialog;
import de.willuhn.jameica.redmine.service.CachingRedmineService;
import de.willuhn.jameica.redmine.util.DurationFormatter;
import de.willuhn.jameica.system.Application;
import de.willuhn.jameica.system.OperationCanceledException;
import de.willuhn.logging.Logger;
import de.willuhn.util.ApplicationException;
import de.willuhn.util.I18N;

/**
 * Zeigt das Systray an.
 */
@Lifecycle(Type.CONTEXT)
public class SysTray
{
  private final static I18N i18n = Application.getPluginLoader().getPlugin(Plugin.class).getResources().getI18N();
  
  private final static DurationFormatter format = new DurationFormatter();
  
  @Resource
  private CachingRedmineService service;
  
  private TrayItem item = null;

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
    final TimeEntry current = service.getCurrentTimeEntry();
    if (current != null)
      stopCurrentTimeEntry();
  }
  

  /**
   * Oeffnet das ContextMenu.
   */
  private void getMenu()
  {
    try
    {
      List<ProjectTree> projects = service.getProjects();

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
              new FileClose().handleAction(null);
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
      //
      ///////////////////////////////////////////////////////////////

      new MenuItem(menu, SWT.SEPARATOR);

      if (projects.size() == 0)
      {
        ///////////////////////////////////////////////////////////////
        // Platzhalter falls die Projekte noch nicht geladen wurden
        MenuItem loading = new MenuItem(menu, SWT.PUSH);
        loading.setText(i18n.tr("Projekte werden geladen..."));
        //
        ///////////////////////////////////////////////////////////////
      }
      else
      {
        ///////////////////////////////////////////////////////////////
        // Liste der Projekte
        for (ProjectTree p:projects)
        {
          createMenu(p,menu);
        }
        //
        ///////////////////////////////////////////////////////////////
        
        ///////////////////////////////////////////////////////////////
        // Menupunkt zum Anhalten des aktuellen Job anzeigen
        final TimeEntry current = service.getCurrentTimeEntry();
        if (current != null)
        {
          new MenuItem(menu, SWT.SEPARATOR);
          MenuItem stop = new MenuItem(menu, SWT.PUSH);
          stop.setText(i18n.tr("Aufgabe anhalten: [{0}] {1}",format.format(current),current.getComment()));
          stop.addListener(SWT.Selection, new Listener()
          {
            public void handleEvent (Event e)
            {
              stopCurrentTimeEntry();
              refresh();
              menu.setVisible(false);
              menu.dispose();
            }
          });
        }
        //
        ///////////////////////////////////////////////////////////////
      }
      
      // Menu anzeigen
      menu.setVisible(true);
    }
    catch (ApplicationException ae)
    {
      Application.getMessagingFactory().sendMessage(new StatusBarMessage(ae.getMessage(),StatusBarMessage.TYPE_ERROR));
      if (GUI.getShell().getMinimized())
      {
        try
        {
          Application.getCallback().notifyUser(ae.getMessage());
        }
        catch (Exception e)
        {
          Logger.error("unable to notify user",e);
        }
      }
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
  private void createMenu(ProjectTree p, final Menu menu) throws ApplicationException
  {
    List<ProjectTree> children = p.getChildren();
    List<Issue> issues = service.getIssues(p.getProject());
    
    // Das Projekt ist leer. Nicht anzeigen
    if (issues == null || issues.size() == 0 && children.size() == 0)
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
    for (final Issue i:issues)
    {
      final MenuItem mi = new MenuItem(sub,SWT.PUSH);
      mi.setData(i);
      mi.setText("#" + i.getId() + " " + i.getSubject());
      mi.addListener(SWT.Selection, new Listener()
      {
        public void handleEvent (Event e)
        {
          // Erst den alten Job ggf. stoppen
          if (stopCurrentTimeEntry())
          {
            // Jetzt einen neuen Job anlegen
            startTimeEntry(i);
            refresh();
          }
          menu.setVisible(false);
          menu.dispose();
        }
      });
    }
  }
  
  /**
   * Stoppt den uebergebenen Task.
   * @param current der zu stoppende Job.
   * @return true, wenn der Job gestoppt wurde oder wenn kein zu stoppender existierte.
   */
  private synchronized boolean stopCurrentTimeEntry()
  {
    final TimeEntry current = service.getCurrentTimeEntry();

    if (current == null)
      return true;
    
    try
    {
      TimeEntryCommitDialog d = new TimeEntryCommitDialog(current);
      d.open();
      service.commitCurrentTimeEntry();
      
      Application.getMessagingFactory().sendMessage(new StatusBarMessage("Erfasste Stunden übernommen",StatusBarMessage.TYPE_SUCCESS));
      return true;
    }
    catch (DismissTimeEntryException de)
    {
      Application.getMessagingFactory().sendMessage(new StatusBarMessage(de.getMessage(),StatusBarMessage.TYPE_INFO));
      service.dismissCurrentTimeEntry();
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
      Logger.error("unable to stop time entry",e);
      Application.getMessagingFactory().sendMessage(new StatusBarMessage(i18n.tr("Stoppen der Zeiterfassung fehlgeschlagen: {0}",e.getMessage()),StatusBarMessage.TYPE_ERROR));
    }
    return false;
  }
  
  /**
   * Startet einen neuen Zeiterfassungsjob fuer das ausgewaehlte Issue.
   * @param issue
   */
  private synchronized void startTimeEntry(Issue issue)
  {
    try
    {
      service.createTimeEntry(issue);
    }
    catch (ApplicationException ae)
    {
      Application.getMessagingFactory().sendMessage(new StatusBarMessage(ae.getMessage(),StatusBarMessage.TYPE_ERROR));
    }
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
          ClassLoader cl = Application.getPluginLoader().getManifest(Plugin.class).getClassLoader();
          TimeEntry current = service.getCurrentTimeEntry();
          if (current != null)
          {
            item.setToolTipText(i18n.tr("[{0}] {1}",new String[]{format.format(current),current.getComment()}));
            item.setImage(SWTUtil.getImage("work.png",cl));
          }
          else
          {
            item.setToolTipText("Keine laufende Zeiterfassung");
            item.setImage(SWTUtil.getImage("clock.png",cl));
          }
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

