package main.java;

public class StudentController {
    private String name;
    
    public StudentController(String name){
        setName(name);
    }

    public String getName(){
        return this.name;
    }
    public void setName(String name){
        this.name = name;
    }
}
