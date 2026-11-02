package com.maplecfo.persistence.sqlite;

import com.maplecfo.categorization.CategoryRule;
import com.maplecfo.domain.Category;
import com.maplecfo.persistence.CategoryRuleRepository;

import java.util.List;

public class SqliteCategoryRuleRepository implements CategoryRuleRepository {

    private final Jdbc jdbc;

    public SqliteCategoryRuleRepository(DatabaseManager db) {
        this.jdbc = new Jdbc(db);
    }

    @Override
    public void save(CategoryRule rule) {
        jdbc.update("INSERT OR REPLACE INTO category_rules (keyword, category) VALUES (?,?)",
                rule.keyword(), rule.category().name());
    }

    @Override
    public List<CategoryRule> findAll() {
        return jdbc.query("SELECT * FROM category_rules",
                rs -> new CategoryRule(rs.getString("keyword"), Category.valueOf(rs.getString("category")), true));
    }
}
