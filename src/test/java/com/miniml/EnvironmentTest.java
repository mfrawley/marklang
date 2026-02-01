package com.miniml;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

import java.util.*;

public class EnvironmentTest {
    private Environment env;
    
    @BeforeEach
    void setUp() {
        env = new Environment();
    }
    
    @Test
    void testDefineAndGet() {
        env.define("x", 42);
        assertEquals(42, env.get("x"));
    }
    
    @Test
    void testGetUndefined() {
        assertThrows(RuntimeException.class, () -> env.get("x"));
    }
    
    @Test
    void testIsDefined() {
        assertFalse(env.isDefined("x"));
        env.define("x", 42);
        assertTrue(env.isDefined("x"));
    }
    
    @Test
    void testMultipleBindings() {
        env.define("x", 10);
        env.define("y", 20);
        env.define("z", 30);
        
        assertEquals(10, env.get("x"));
        assertEquals(20, env.get("y"));
        assertEquals(30, env.get("z"));
    }
    
    @Test
    void testSet() {
        env.define("x", 10);
        assertEquals(10, env.get("x"));
        
        env.set("x", 20);
        assertEquals(20, env.get("x"));
    }
    
    @Test
    void testSetUndefined() {
        assertThrows(RuntimeException.class, () -> env.set("x", 42));
    }
    
    @Test
    void testExtend() {
        env.define("x", 10);
        
        Environment child = env.extend();
        child.define("y", 20);
        
        assertEquals(10, child.get("x"));
        assertEquals(20, child.get("y"));
        
        assertEquals(10, env.get("x"));
        assertThrows(RuntimeException.class, () -> env.get("y"));
    }
    
    @Test
    void testExtendShadowing() {
        env.define("x", 10);
        
        Environment child = env.extend();
        child.define("x", 20);
        
        assertEquals(20, child.get("x"));
        assertEquals(10, env.get("x"));
    }
    
    @Test
    void testNestedExtend() {
        env.define("x", 10);
        
        Environment child1 = env.extend();
        child1.define("y", 20);
        
        Environment child2 = child1.extend();
        child2.define("z", 30);
        
        assertEquals(10, child2.get("x"));
        assertEquals(20, child2.get("y"));
        assertEquals(30, child2.get("z"));
    }
    
    @Test
    void testSetInParent() {
        env.define("x", 10);
        
        Environment child = env.extend();
        child.set("x", 20);
        
        assertEquals(20, child.get("x"));
        assertEquals(20, env.get("x"));
    }
    
    @Test
    void testFromMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("x", 10);
        map.put("y", 20);
        
        Environment env = Environment.fromMap(map);
        
        assertEquals(10, env.get("x"));
        assertEquals(20, env.get("y"));
    }
    
    @Test
    void testGetSlot() {
        env.define("x", 10);
        env.define("y", 20);
        
        assertEquals(0, env.getSlot("x"));
        assertEquals(1, env.getSlot("y"));
        assertNull(env.getSlot("z"));
    }
    
    @Test
    void testGetSlotInParent() {
        env.define("x", 10);
        
        Environment child = env.extend();
        child.define("y", 20);
        
        assertNotNull(child.getSlot("x"));
        assertNotNull(child.getSlot("y"));
    }
}
