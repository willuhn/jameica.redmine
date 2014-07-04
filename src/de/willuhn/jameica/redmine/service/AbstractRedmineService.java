/**********************************************************************
 *
 * Copyright (c) by Olaf Willuhn
 * All rights reserved
 *
 **********************************************************************/

package de.willuhn.jameica.redmine.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Resource;

import org.apache.commons.lang.StringUtils;

import com.taskadapter.redmineapi.NotAuthorizedException;
import com.taskadapter.redmineapi.NotFoundException;
import com.taskadapter.redmineapi.RedmineAuthenticationException;
import com.taskadapter.redmineapi.RedmineException;
import com.taskadapter.redmineapi.RedmineManager;
import com.taskadapter.redmineapi.RedmineTransportException;
import com.taskadapter.redmineapi.bean.Issue;
import com.taskadapter.redmineapi.bean.Membership;
import com.taskadapter.redmineapi.bean.Project;
import com.taskadapter.redmineapi.bean.TimeEntry;
import com.taskadapter.redmineapi.bean.TimeEntryActivity;
import com.taskadapter.redmineapi.bean.User;

import de.willuhn.jameica.redmine.Plugin;
import de.willuhn.jameica.redmine.Settings;
import de.willuhn.jameica.redmine.beans.ProjectTree;
import de.willuhn.jameica.system.Application;
import de.willuhn.logging.Level;
import de.willuhn.logging.Logger;
import de.willuhn.util.ApplicationException;
import de.willuhn.util.I18N;

/**
 * Abstrakter Basis-Service, der den Zugriff auf die Redmine-API bereitstellt.
 */
public class AbstractRedmineService
{
  private final static I18N i18n = Application.getPluginLoader().getPlugin(Plugin.class).getResources().getI18N();
  
  @Resource
  private Settings settings;
  
  private RedmineManager manager = null;
  private User currentUser = null;
  private TimeEntry currentEntry = null;
  
  /**
   * Liefert den Redmine-Manager.
   * @return der Redmine-Manager.
   * @throws ApplicationException
   */
  private synchronized RedmineManager getManager() throws ApplicationException
  {
    if (this.manager == null)
    {
      String url    = this.settings.getUrl();
      String apiKey = this.settings.getApiKey();
      
      if (StringUtils.isEmpty(url) || StringUtils.isEmpty(apiKey))
        throw new ApplicationException(i18n.tr("Bitte geben Sie die URL und Ihren API-Zugriffsschlüssel unter Datei->Einstellungen ein."));
      
      Logger.info("opening connection to: " + url);
      Logger.debug("using API key: " + apiKey);
      this.manager = new RedmineManager(url,apiKey);
    }
    
    return this.manager;
  }
  
  /**
   * Verwirft den aktuellen Redmine-Manager und forciert damit einen neuen Verbindungsaufbau.
   */
  public void reconnect()
  {
    if (this.manager != null)
    {
      Logger.info("discarding current redmine manager, results in server reconnect");
      this.manager = null;
    }
  }
  
  /**
   * Liefert den aktuell angemeldeten Benutzer.
   * @return der aktuell angemeldete Benutzer.
   * @throws ApplicationException
   */
  public User getCurrentUser() throws ApplicationException
  {
    if (this.currentUser == null)
    {
      try
      {
        Logger.info("fetching current currentUser");
        this.currentUser = this.getManager().getCurrentUser();
      }
      catch (RedmineException re)
      {
        handleRedmineException(re,i18n.tr("Aktueller User nicht ermittelbar: {0}",re.getMessage()));
        return null; // cannot happen
      }
    }
    return this.currentUser;
  }
  
  /**
   * Liefert eine baumfoermige Liste der Projekte, auf die der User Zugriff hat.
   * @return die Liste der Projekte, auf die der User Zugriff hat.
   * @throws ApplicationException
   */
  public List<ProjectTree> getProjects() throws ApplicationException
  {
    List<Project> projects = this.getProjectList();
    List<ProjectTree> result = new ArrayList<ProjectTree>();
    
    // 1. Lookup fuer die IDs
    Map<Integer,ProjectTree> lookup = new HashMap<Integer,ProjectTree>();
    for (Project p:projects)
    {
      ProjectTree pt = new ProjectTree();
      pt.setProject(p);
      lookup.put(p.getId(),pt);
    }
    
    // 2. Baum-Struktur aufbauen
    for (Project p:projects)
    {
      Integer id  = p.getId();
      Integer pid = p.getParentId();
      
      ProjectTree pt = lookup.get(id);
      
      // Projekt auf Root-Ebene. Zur Ergebnis-Liste hinzufuegen und beenden.
      if (pid == null)
      {
        result.add(pt);
        continue;
      }
      
      // Kind-Projekt. Parent ermitteln, an das das Projekt gehaengt werden kann.
      ProjectTree parent = lookup.get(pid);
      if (parent == null)
      {
        Logger.error("SUSPECT: Parent project (ID: " + pid + ") not found for project (ID: " + id + ", name: " + p.getIdentifier() + "), skipping");
        continue;
      }
      
      parent.getChildren().add(pt);
    }
    
    return result;
  }
  
  /**
   * Liefert die Liste der Issues fuer das Projekt.
   * @param project das Projekt.
   * @return die Issues.
   * @throws ApplicationException
   */
  public List<Issue> getIssues(Project project) throws ApplicationException
  {
    if (project == null)
      throw new ApplicationException(i18n.tr("Bitte wählen Sie ein Projekt aus"));

    try
    {
      Logger.info("fetching issues for project " + project.getIdentifier());
      List<Issue> issues = this.getManager().getIssues(project.getIdentifier(),null);
      return issues;
    }
    catch (NotAuthorizedException e)
    {
      // Kann passieren, wenn das Feature "Tickets" im Projekt gar nicht aktiviert ist
      Logger.write(Level.DEBUG,"not allowed to see issues or ticket feature not enabled for project: " + project.getIdentifier(),e);
      return Collections.emptyList();
    }
    catch (RedmineException re)
    {
      handleRedmineException(re,i18n.tr("Ticket-Abruf fehlgeschlagen für Projekt \"{0}\": {1}",project.getIdentifier(),re.getMessage()));
      return null; // cannot happen
    }
  }
  
  /**
   * Liefert die verfuegbaren Aktivitaeten fuer die Zeiterfassung.
   * @return die verfuegbaren Aktivitaeten fuer die Zeiterfassung.
   * @throws ApplicationException
   */
  public List<TimeEntryActivity> getActivities() throws ApplicationException
  {
    try
    {
      Logger.info("fetching time-entry activities");
      List<TimeEntryActivity> activities = this.getManager().getTimeEntryActivities();
      return activities;
    }
    catch (RedmineException re)
    {
      handleRedmineException(re,i18n.tr("Abruf der verfügbaren Aktivitäten fehlgeschlagen"));
      return null; // cannot happen
    }
  }
  
  /**
   * Liefert den aktuellen Zeiterfassungsjob.
   * @return der aktuelle Zeiterfassungsjob.
   */
  public TimeEntry getCurrentTimeEntry()
  {
    return this.currentEntry;
  }

  /**
   * Startet eine neue Zeiterfassung auf dem Issue.
   * @param issue das Issue.
   * @return der erstellte Zeiterfassungsjob.
   * @throws ApplicationException
   */
  public TimeEntry createTimeEntry(Issue issue) throws ApplicationException
  {
    if (this.currentEntry != null)
      throw new ApplicationException(i18n.tr("Derzeit läuft bereits eine Zeiterfassung für die Aufgabe \"{0}\"",this.currentEntry.getComment()));
    
    Logger.info("creating new time entry for issue #" + issue.getId());

    Date now = new Date();
    
    this.currentEntry = new TimeEntry();
    this.currentEntry.setProjectId(issue.getProject().getId());
    this.currentEntry.setIssueId(issue.getId());
    this.currentEntry.setComment("#" + issue.getId() + " " + issue.getSubject());
    this.currentEntry.setCreatedOn(now);
    this.currentEntry.setSpentOn(now);
    this.currentEntry.setUserId(this.getCurrentUser().getId());
    return this.currentEntry;
  }
  
  /**
   * Schliesst die Zeiterfassung fuer die laufende Aufgabe ab.
   * Das Attribut "activity" vom Aufrufer vorher gesetzt worden sein.
   * @throws ApplicationException
   */
  public synchronized void commitCurrentTimeEntry() throws ApplicationException
  {
    if (this.currentEntry == null)
      throw new ApplicationException(i18n.tr("Derzeit läuft keine eine Zeiterfassung"));
    
    if (this.currentEntry.getActivityId() == null)
      throw new ApplicationException(i18n.tr("Keine Aktivität ausgewählt"));
    
    try
    {
      // 1. "hours" errechnen
      long now      = System.currentTimeMillis();
      long started  = this.currentEntry.getCreatedOn().getTime();
      float minutes = (now - started) / 1000 / 60;
      float hours   = minutes / 60;
      this.currentEntry.setHours(hours);

      Logger.info("committing current time entry for issue #" + this.currentEntry.getIssueId() + ", used time: " + minutes + " minutes (" + hours + " hours)");

      // 2. Speichern
      this.getManager().createTimeEntry(this.currentEntry);
      Logger.info("time entry for issue #" + this.currentEntry.getIssueId() + " committed");
      this.currentEntry = null; // Nicht im finally damit das nur passiert, wenn das commit geklappt hat
    }
    catch (RedmineException re)
    {
      handleRedmineException(re,i18n.tr("Übernehmen der erfassten Arbeitszeit fehlgeschlagen: {0}",re.getMessage()));
    }
  }
  
  /**
   * Liefert eine Liste der Projekte, auf die der User Zugriff hat.
   * @return die Liste der Projekte, auf die der User Zugriff hat.
   * @throws ApplicationException
   */
  private List<Project> getProjectList() throws ApplicationException
  {
    try
    {
      RedmineManager manager = this.getManager();
      User user = this.getCurrentUser();
      
      Logger.info("fetching project list");
      List<Project> projects = manager.getProjects();
      List<Project> filtered = new ArrayList<Project>();
      for (Project p:projects)
      {
        List<Membership> members = manager.getMemberships(p);
        for (Membership m:members)
        {
          User u = m.getUser();
          if (u == null || u.equals(user))
          {
            filtered.add(p);
            break;
          }
        }
      }
      
      return filtered;
    }
    catch (RedmineException re)
    {
      handleRedmineException(re,i18n.tr("Projekt-Liste kann nicht abgerufen werden: {0}",re.getMessage()));
      return null; // cannot happen
    }
  }
  
  /**
   * Fuehrt die Fehlerbehandlung durch.
   * @param e die Exception von Redmine.
   * @param message die dem User alternativ anzuzeigende Fehlermeldung.
   * @throws ApplicationException
   */
  private void handleRedmineException(RedmineException e, String message) throws ApplicationException
  {
    Logger.error("exception occured during redmine access",e);
    
    try
    {
      throw e;
    }
    catch (NotFoundException e1)
    {
      throw new ApplicationException(i18n.tr("Redmine Server-URL scheint ungültig zu sein"));
    }
    catch (RedmineTransportException e2)
    {
      throw new ApplicationException(i18n.tr("Hostname des Redmine-Servers scheint falsch zu sein oder Server nicht erreichbar."));
    }
    catch (IllegalArgumentException e3)
    {
      throw new ApplicationException(i18n.tr("Bitte geben Sie die URL des Redmine-Servers unter Datei->Einstellungen ein."));
    }
    catch (RedmineAuthenticationException e4)
    {
      throw new ApplicationException(i18n.tr("API-Zugangsschlüssel fehlt oder ungültig."));
    }
    catch (RedmineException re)
    {
      // Hierfuer haben wir keine gesonderte Behandlung. Daher die Default-Fehlermeldung und Logging oben.
    }
    
    throw new ApplicationException(message);
  }
  
}


