/**********************************************************************
 *
 * Copyright (c) by Olaf Willuhn
 * All rights reserved
 *
 **********************************************************************/

package de.willuhn.jameica.redmine.gui.dialogs;

import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;

import com.taskadapter.redmineapi.bean.TimeEntry;
import com.taskadapter.redmineapi.bean.TimeEntryActivity;

import de.willuhn.jameica.gui.Action;
import de.willuhn.jameica.gui.dialogs.AbstractDialog;
import de.willuhn.jameica.gui.input.LabelInput;
import de.willuhn.jameica.gui.input.SelectInput;
import de.willuhn.jameica.gui.input.TextInput;
import de.willuhn.jameica.gui.parts.ButtonArea;
import de.willuhn.jameica.gui.util.Container;
import de.willuhn.jameica.gui.util.SimpleContainer;
import de.willuhn.jameica.redmine.Plugin;
import de.willuhn.jameica.redmine.service.CachingRedmineService;
import de.willuhn.jameica.redmine.util.DurationFormatter;
import de.willuhn.jameica.services.BeanService;
import de.willuhn.jameica.system.Application;
import de.willuhn.jameica.system.OperationCanceledException;
import de.willuhn.util.ApplicationException;
import de.willuhn.util.I18N;

/**
 * Dialog zum Committen eines Time-Entry.
 */
public class TimeEntryCommitDialog extends AbstractDialog
{
  private final static I18N i18n = Application.getPluginLoader().getPlugin(Plugin.class).getResources().getI18N();
  
  private final static int WINDOW_WIDTH = 400;

  private TimeEntry entry = null;
  private SelectInput activities = null;
  private TextInput comment = null;
  
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
    Container group = new SimpleContainer(parent);
    
    LabelInput duration = new LabelInput(new DurationFormatter().format(this.entry));
    duration.setName(i18n.tr("Erfasste Zeit"));
    group.addInput(duration);
    group.addInput(this.getActivities());
    group.addInput(this.getComment());

    ButtonArea b = new ButtonArea();
    b.addButton(i18n.tr("Übernehmen"), new Action()
    {
      public void handleAction(Object context) throws ApplicationException
      {
        TimeEntryActivity a = (TimeEntryActivity) getActivities().getValue();
        entry.setActivityId(a.getId());
        entry.setComment((String) getComment().getValue());
        close();
      }
    },null,true,"ok.png");
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

}


