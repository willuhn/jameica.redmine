/**********************************************************************
 *
 * Copyright (c) by Olaf Willuhn
 * All rights reserved
 *
 **********************************************************************/

package de.willuhn.jameica.redmine.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Resource;

import com.taskadapter.redmineapi.bean.Issue;
import com.taskadapter.redmineapi.bean.Project;
import com.taskadapter.redmineapi.bean.TimeEntryActivity;
import com.taskadapter.redmineapi.bean.User;

import de.willuhn.annotation.Lifecycle;
import de.willuhn.annotation.Lifecycle.Type;
import de.willuhn.jameica.messaging.StatusBarMessage;
import de.willuhn.jameica.redmine.Settings;
import de.willuhn.jameica.redmine.beans.ProjectTree;
import de.willuhn.jameica.system.Application;
import de.willuhn.logging.Logger;
import de.willuhn.util.ApplicationException;

/**
 * Redmine-Service, der die Ergebnisse fuer schnelleren Zugriff cached.
 */
@Lifecycle(Type.CONTEXT)
public class CachingRedmineService extends AbstractRedmineService
{
  @Resource
  private Settings settings;

  private Object lock = new Object();
  // Mit leeren Listen/Maps initialisieren
  private List<ProjectTree> projects = new ArrayList<ProjectTree>();
  private List<TimeEntryActivity> activities = new ArrayList<TimeEntryActivity>();
  private Map<Integer,List<Issue>> issues = new HashMap<Integer,List<Issue>>();
  
  /**
   * @see de.willuhn.jameica.redmine.service.AbstractRedmineService#getProjects()
   */
  @Override
  public List<ProjectTree> getProjects() throws ApplicationException
  {
    // Wir liefern hier generell nur gecachten Daten zurueck
    synchronized (lock)
    {
      return this.projects;
    }
  }
  
  /**
   * @see de.willuhn.jameica.redmine.service.AbstractRedmineService#getIssues(com.taskadapter.redmineapi.bean.Project)
   */
  @Override
  public List<Issue> getIssues(Project project) throws ApplicationException
  {
    // Wir liefern hier generell nur gecachten Daten zurueck
    synchronized (lock)
    {
      List<Issue> issues = this.issues.get(project.getId());
      
      boolean own = this.settings.getOnlyOwnIssues();
      boolean ua  = this.settings.getUnassignedIssues();
      
      if (issues == null || issues.size() == 0)
        return issues;

      List<Issue> result = new ArrayList<Issue>();

      User me = this.getCurrentUser();
      for (Issue i:issues)
      {
        // Nur die direkt dem Projekt zugeordneten Aufgaben. Nicht die von Unter-Projekten.
        // Die kommen ja in einem extra Ordner.
        Project p = i.getProject();
        if (!p.equals(project))
          continue;

        // Keinem zugeordnet - sollen die geliefert werden?
        User assignee = i.getAssignee();
        if (assignee == null)
        {
          if (ua) // Nur hinzufuegen, wenn gewuenscht
            result.add(i);
          continue;
        }
        
        // Keine Einschraenkung beim User oder User passt
        if (!own || assignee.equals(me))
          result.add(i);
      }
      return result;

    }
  }
  
  /**
   * @see de.willuhn.jameica.redmine.service.AbstractRedmineService#getActivities()
   */
  @Override
  public List<TimeEntryActivity> getActivities() throws ApplicationException
  {
    // Wir liefern hier generell nur gecachten Daten zurueck
    synchronized (lock)
    {
      return this.activities;
    }
  }
  
  /**
   * @see de.willuhn.jameica.redmine.service.AbstractRedmineService#reconnect()
   */
  @Override
  public void reconnect()
  {
    // Wir laden die Daten erstmal komplett neu, ohne die GUI zu blockieren und
    // uebernehmen die Daten dann en bloc
    super.reconnect();

    long started = System.currentTimeMillis();
    Logger.info("refreshing cache");
    
    try
    {
      // 1. Projekte
      List<ProjectTree> newProjects = super.getProjects();
      
      // 2. Activities
      List<TimeEntryActivity> newActivities = super.getActivities();
      
      // 3. Issues
      Map<Integer,List<Issue>> newIssues = new HashMap<Integer,List<Issue>>();
      for (ProjectTree p:newProjects)
      {
        this.reloadIssues(p,newIssues);
      }
      
      // Und jetzt alles am Stueck uebernehmen
      synchronized (lock)
      {
        this.projects = newProjects;
        this.activities = newActivities;
        this.issues = newIssues;
      }
      
      Logger.info("refreshing finished, loaded " + + this.issues.size() + " projects, took " + ((System.currentTimeMillis() - started) / 1000) + " seconds");
    }
    catch (ApplicationException ae)
    {
      Application.getMessagingFactory().sendMessage(new StatusBarMessage(ae.getMessage(),StatusBarMessage.TYPE_ERROR));
    }
  }
  
  /**
   * Fuehrt ein rekursives Reload der Issues in dem Project-Baum durch.
   * @param p der Projekt-Baum.
   * @param newIssues die neue Map mit den Issues.
   * @throws ApplicationException
   */
  private void reloadIssues(ProjectTree p, Map<Integer,List<Issue>> newIssues) throws ApplicationException
  {
    // Das Projekt selbst
    Project project = p.getProject();
    newIssues.put(project.getId(),super.getIssues(project));
    
    // Jetzt die Kinder
    for (ProjectTree child:p.getChildren())
    {
      this.reloadIssues(child,newIssues);
    }
  }
}


