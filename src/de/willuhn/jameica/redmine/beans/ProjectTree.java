/**********************************************************************
 *
 * Copyright (c) by Olaf Willuhn
 * All rights reserved
 *
 **********************************************************************/

package de.willuhn.jameica.redmine.beans;

import java.util.LinkedList;
import java.util.List;

import com.taskadapter.redmineapi.bean.Project;

/**
 * Packt die Redmine-Projektliste in einen Baum.
 */
public class ProjectTree
{
  private Project project = null;
  private List<ProjectTree> children = new LinkedList<ProjectTree>();
  
  /**
   * Liefert das Projekt selbst.
   * @return das Projekt selbst.
   */
  public Project getProject()
  {
    return project;
  }
  
  /**
   * Speichert das Projekt selbst.
   * @param project das Projekt selbst.
   */
  public void setProject(Project project)
  {
    this.project = project;
  }
  
  /**
   * Liefert die Liste der Kind-Projekte.
   * @return die Liste der Kind-Projekte.
   */
  public List<ProjectTree> getChildren()
  {
    return children;
  }
  
  /**
   * Speichert die Liste der Kind-Projekte.
   * @param children Liste der Kind-Projekte.
   */
  public void setChildren(List<ProjectTree> children)
  {
    this.children = children;
  }

  
}


