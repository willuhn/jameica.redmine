/**********************************************************************
 *
 * Copyright (c) by Olaf Willuhn
 * All rights reserved
 *
 **********************************************************************/

package de.willuhn.jameica.redmine.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Resource;

import com.taskadapter.redmineapi.NotAuthorizedException;
import com.taskadapter.redmineapi.RedmineException;
import com.taskadapter.redmineapi.RedmineManager;
import com.taskadapter.redmineapi.bean.Issue;
import com.taskadapter.redmineapi.bean.Membership;
import com.taskadapter.redmineapi.bean.Project;
import com.taskadapter.redmineapi.bean.User;

import de.willuhn.annotation.Lifecycle;
import de.willuhn.annotation.Lifecycle.Type;
import de.willuhn.jameica.redmine.Plugin;
import de.willuhn.jameica.redmine.Settings;
import de.willuhn.jameica.redmine.beans.ProjectTree;
import de.willuhn.jameica.system.Application;
import de.willuhn.logging.Level;
import de.willuhn.logging.Logger;
import de.willuhn.util.ApplicationException;
import de.willuhn.util.I18N;

/**
 * Bean, die den Zugriff auf die Redmine-API bereitstellt.
 */
@Lifecycle(Type.CONTEXT)
public class RedmineService
{
  private final static I18N i18n = Application.getPluginLoader().getPlugin(Plugin.class).getResources().getI18N();
  
  @Resource
  private Settings settings;
  
  private RedmineManager manager = null;
  private User currentUser = null;
  private Issue currentIssue = null;
  
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
      
      Logger.info("opening connection to: " + url);
      Logger.debug("using API key: " + apiKey);
      this.manager = new RedmineManager(url,apiKey);
    }
    
    return this.manager;
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
        Logger.error("unable to fetch current currentUser",re);
        throw new ApplicationException(i18n.tr("Aktueller User nicht ermittelbar: {0}",re.getMessage()));
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
      Logger.error("unable to fetch issue list for project " + project.getIdentifier(),re);
      throw new ApplicationException(i18n.tr("Ticket-Abruf fehlgeschlagen für Projekt \"{0}\": {1}",project.getIdentifier(),re.getMessage()));
    }
  }
  
  /**
   * Liefert das aktuell laufende Issue.
   * @return das aktuell laufende Issue.
   */
  public Issue getCurrentIssue()
  {
    return currentIssue;
  }

  /**
   * Speichert das aktuell laufende Issue.
   * @param currentIssue das aktuell laufende Issue.
   */
  public void setCurrentIssue(Issue currentIssue)
  {
    this.currentIssue = currentIssue;
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
      Logger.error("unable to fetch project list",re);
      throw new ApplicationException(i18n.tr("Projekt-Liste kann nicht abgerufen werden: {0}",re.getMessage()));
    }
  }
  
}


