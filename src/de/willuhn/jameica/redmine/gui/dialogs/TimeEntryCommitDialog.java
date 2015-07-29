/**********************************************************************
 *
 * Copyright (c) by Olaf Willuhn
 * All rights reserved
 *
 **********************************************************************/

package de.willuhn.jameica.redmine.gui.dialogs;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;

import com.taskadapter.redmineapi.bean.TimeEntry;
import com.taskadapter.redmineapi.bean.TimeEntryActivity;

import de.willuhn.jameica.gui.Action;
import de.willuhn.jameica.gui.dialogs.AbstractDialog;
import de.willuhn.jameica.gui.input.DateInput;
import de.willuhn.jameica.gui.input.LabelInput;
import de.willuhn.jameica.gui.input.SelectInput;
import de.willuhn.jameica.gui.input.TextInput;
import de.willuhn.jameica.gui.parts.Button;
import de.willuhn.jameica.gui.parts.ButtonArea;
import de.willuhn.jameica.gui.parts.NotificationPanel;
import de.willuhn.jameica.gui.parts.NotificationPanel.Type;
import de.willuhn.jameica.gui.util.Container;
import de.willuhn.jameica.gui.util.SimpleContainer;
import de.willuhn.jameica.redmine.DismissTimeEntryException;
import de.willuhn.jameica.redmine.Plugin;
import de.willuhn.jameica.redmine.service.CachingRedmineService;
import de.willuhn.jameica.redmine.util.DurationFormatter;
import de.willuhn.jameica.services.BeanService;
import de.willuhn.jameica.system.Application;
import de.willuhn.jameica.system.OperationCanceledException;
import de.willuhn.logging.Level;
import de.willuhn.logging.Logger;
import de.willuhn.util.ApplicationException;
import de.willuhn.util.I18N;

/**
 * Dialog zum Committen eines Time-Entry.
 */
public class TimeEntryCommitDialog extends AbstractDialog
{
  private final static I18N i18n = Application.getPluginLoader().getPlugin(Plugin.class).getResources().getI18N();
  
  private final static DateFormat HF = new SimpleDateFormat("HH:mm");
  
  private final static int WINDOW_WIDTH = 450;

  private NotificationPanel panel = null;
  private TimeEntry entry = null;
  private LabelInput duration = null;
  private DateInput date = null;
  private TextInput started = null;
  private SelectInput activities = null;
  private TextInput comment = null;
  private Button apply = null;
  
  /**
   * ct.
   * @param entry der Entry fuer den die Zeit uebernommen werden soll.
   */
  public TimeEntryCommitDialog(TimeEntry entry)
  {
    super(POSITION_CENTER);
    this.setTitle(i18n.tr("Erfasste Zeit übernehmen"));
    this.setSize(WINDOW_WIDTH,SWT.DEFAULT);
    this.entry = entry;
  }

  /**
   * @see de.willuhn.jameica.gui.dialogs.AbstractDialog#paint(org.eclipse.swt.widgets.Composite)
   */
  protected void paint(Composite parent) throws Exception
  {
    this.getPanel().paint(parent);
    Container group = new SimpleContainer(parent);
    
    group.addInput(this.getDate());
    group.addInput(this.getStarted());
    group.addInput(this.getDuration());
    group.addInput(this.getActivities());
    group.addInput(this.getComment());

    ButtonArea b = new ButtonArea();

    b.addButton(this.getApplyButton());
    b.addButton(i18n.tr("Erfasste Zeit verwerfen"), new Action()
    {
      public void handleAction(Object context) throws ApplicationException
      {
        boolean doit = false;
        try
        {
          doit = (Application.getCallback().askUser(i18n.tr("Wollen Sie die erfasste Arbeitszeit wirklich verwerfen?")));
        }
        catch (Exception e)
        {
          Logger.error("unable to ask user",e);
        }
        
        if (doit)
          throw new DismissTimeEntryException(i18n.tr("Erfasste Arbeitszeit verworfen"));
      }
    },null,false,"user-trash-full.png");
    b.addButton(i18n.tr("Abbrechen"), new Action()
    {
      public void handleAction(Object context) throws ApplicationException
      {
        throw new OperationCanceledException();
      }
    },null,false,"process-stop.png");
    
    group.addButtonArea(b);
    
    getShell().setMinimumSize(getShell().computeSize(WINDOW_WIDTH,SWT.DEFAULT));

  }
  
  /**
   * @see de.willuhn.jameica.gui.dialogs.AbstractDialog#getData()
   */
  @Override
  protected Object getData() throws Exception
  {
    return this.entry;
  }
  
  /**
   * Liefert den Uebernehmen-Button.
   * @return der Uebernehmen-Button.
   */
  private Button getApplyButton()
  {
    if (this.apply != null)
      return this.apply;
    
    this.apply = new Button(i18n.tr("Übernehmen"), new Action()
    {
      public void handleAction(Object context) throws ApplicationException
      {
        if (validate(true))
        {
          TimeEntryActivity a = (TimeEntryActivity) getActivities().getValue();
          entry.setActivityId(a.getId());
          entry.setComment((String) getComment().getValue());
          close();
        }
      }
    },null,true,"ok.png");
    
    return this.apply;
  }
  
  /**
   * Liefert ein Eingabefeld fuer die Startzeit der Taetigkeit.
   * Kann nachtraeglich noch geandert werden.
   * @return Eingabefeld.
   */
  private TextInput getStarted()
  {
    if (this.started != null)
      return this.started;
    
    this.started = new TextInput(HF.format(this.entry.getCreatedOn()),5);
    this.started.setValidChars("0123456789:");
    this.started.setMandatory(true);
    this.started.setName(i18n.tr("Beginn der Zeiterfassung (hh:mm)"));
    this.started.addListener(new Listener() {
      
      @Override
      public void handleEvent(Event event)
      {
        getApplyButton().setEnabled(validate(false));
      }
    });
    
    return this.started;
  }

  /**
   * Liefert ein Eingabefeld fuer das Datum der Taetigkeit.
   * Kann nachtraeglich noch geandert werden.
   * @return Eingabefeld.
   */
  private DateInput getDate()
  {
    if (this.date != null)
      return this.date;
    
    this.date = new DateInput(this.entry.getCreatedOn());
    this.date.setMandatory(true);
    this.date.setName(i18n.tr("Datum"));
    this.date.addListener(new Listener() {
      
      @Override
      public void handleEvent(Event event)
      {
        getApplyButton().setEnabled(validate(false));
      }
    });
    
    return this.date;
  }

  /**
   * Liefert ein Label mit der erfassten Zeit.
   * @return ein Label mit der erfassten Zeit.
   */
  private LabelInput getDuration()
  {
    if (this.duration != null)
      return this.duration;
    
    this.duration = new LabelInput(new DurationFormatter().format(this.entry));
    this.duration.setName(i18n.tr("Erfasste Zeit"));
    return this.duration;
  }
  
  /**
   * Liefert eine Liste der verfuegbaren Aktivitaeten.
   * @return eine Liste der verfuegbaren Aktivitaeten.
   * @throws ApplicationException
   */
  private SelectInput getActivities() throws ApplicationException
  {
    if (this.activities != null)
      return this.activities;
    
    BeanService bs = Application.getBootLoader().getBootable(BeanService.class);
    CachingRedmineService service = bs.get(CachingRedmineService.class);
    
    List<TimeEntryActivity> list = service.getActivities();
    TimeEntryActivity preselected = null;
    for (TimeEntryActivity a:list)
    {
      if (a.isDefault())
      {
        preselected = a;
        break;
      }
    }
    
    this.activities = new SelectInput(service.getActivities(),preselected);
    this.activities.setAttribute("name");
    this.activities.setName(i18n.tr("Aktivität"));
    this.activities.setMandatory(true);
    return this.activities;
  }
  
  /**
   * Liefert ein Eingabefeld fuer einen optionalen Kommentar.
   * @return Eingabefeld fuer einen optionalen Kommentar.
   */
  private TextInput getComment()
  {
    if (this.comment != null)
      return this.comment;
    
    this.comment = new TextInput(null);
    this.comment.setName(i18n.tr("Kommentar"));
    return this.comment;
  }

  /**
   * Liefert ein Panel zur Anzeige von Hinweisen/Fehlern.
   * @return ein Panel zur Anzeige von Hinweisen/Fehlern.
   */
  private NotificationPanel getPanel()
  {
    if (this.panel != null)
      return this.panel;
    
    this.panel = new NotificationPanel();
    return this.panel;
  }
  
  /**
   * Validiert die aktuellen Eingaben.
   * @param apply true, wenn die Daten in den TimeEntry uebernommen werden sollen.
   * @return true, wenn die Validierung ok ist.
   */
  private boolean validate(boolean apply)
  {
    try
    {
      // 1. Start-Zeit berechnen
      Date dt = HF.parse((String)this.getStarted().getValue());
      Calendar cal = Calendar.getInstance();
      cal.setTime(dt);
      
      Date day = (Date) this.getDate().getValue();
      Calendar newStart = Calendar.getInstance();
      newStart.setTime(day != null ? day : this.entry.getCreatedOn());
      newStart.set(Calendar.HOUR_OF_DAY,cal.get(Calendar.HOUR_OF_DAY));
      newStart.set(Calendar.MINUTE,cal.get(Calendar.MINUTE));

      Date started = newStart.getTime();
      int minutes = (int) (System.currentTimeMillis() - started.getTime()) / 1000 / 60;

      // 2. Bisherige Arbeitszeit basierend darauf aktualisieren
      this.getDuration().setValue(new DurationFormatter().format(minutes));
      
      if (apply)
      {
        this.entry.setCreatedOn(started);
      }
      return true;
    }
    catch (Exception e)
    {
      Logger.write(Level.DEBUG,"invalid start time",e);
      this.getPanel().setText(Type.ERROR,i18n.tr("Bitte geben Sie eine gültige Uhrzeit im Format hh:mm ein"));
      this.getStarted().focus();
      return false;
    }
  }
}


