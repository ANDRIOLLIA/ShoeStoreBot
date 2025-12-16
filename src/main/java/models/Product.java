package models;

import java.util.List;

public class Product {
    private int id;
    private String productCode;
    private String name;
    private int price;
    private List<Integer> sizes;
    private String description;
    private String photoPath;
    private String brand;
    private String gender;

    // Конструктор по умолчанию
    public Product() {
    }

    // Полный конструктор
    public Product(int id, String productCode, String name, int price, List<Integer> sizes,
                   String description, String photoPath, String brand, String gender) {
        this.id = id;
        this.productCode = productCode;
        this.name = name;
        this.price = price;
        this.sizes = sizes;
        this.description = description;
        this.photoPath = photoPath;
        this.brand = brand;
        this.gender = gender;
    }

    // Конструктор без brand и gender (для обратной совместимости)
    public Product(int id, String productCode, String name, int price, List<Integer> sizes,
                   String description, String photoPath) {
        this(id, productCode, name, price, sizes, description, photoPath, "unknown", "MEN");
    }

    // Геттеры и сеттеры
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getProductCode() {
        return productCode;
    }

    public void setProductCode(String productCode) {
        this.productCode = productCode;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getPrice() {
        return price;
    }

    public void setPrice(int price) {
        this.price = price;
    }

    public List<Integer> getSizes() {
        return sizes;
    }

    public void setSizes(List<Integer> sizes) {
        this.sizes = sizes;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getPhotoPath() {
        return photoPath;
    }

    public void setPhotoPath(String photoPath) {
        this.photoPath = photoPath;
    }

    public String getBrand() {
        return brand;
    }

    public void setBrand(String brand) {
        this.brand = brand;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    // Метод для проверки наличия определенного размера
    public boolean hasSize(int size) {
        return sizes != null && sizes.contains(size);
    }

    // Метод для получения размера как строки
    public String getSizesAsString() {
        if (sizes == null || sizes.isEmpty()) {
            return "Не указаны";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < sizes.size(); i++) {
            sb.append(sizes.get(i));
            if (i < sizes.size() - 1) {
                sb.append(", ");
            }
        }
        return sb.toString();
    }

    // Метод для получения информации о товаре
    public String getProductInfo() {
        return String.format(
                "🛍️ %s\n💰 Цена: %d₽\n📝 %s\n📊 Размеры: %s\n🏷️ Бренд: %s\n👥 Категория: %s",
                name, price, description, getSizesAsString(), brand,
                gender.equals("MEN") ? "Мужская" : (gender.equals("WOMEN") ? "Женская" : "Унисекс")
        );
    }

    // Метод для получения краткой информации
    public String getShortInfo() {
        return String.format("%s - %d₽", name, price);
    }

    @Override
    public String toString() {
        return String.format(
                "Product{id=%d, code='%s', name='%s', price=%d, sizes=%s, brand='%s', gender='%s'}",
                id, productCode, name, price, sizes, brand, gender
        );
    }

    // Метод для клонирования товара
    public Product copy() {
        return new Product(id, productCode, name, price, sizes, description, photoPath, brand, gender);
    }

    // Метод для сравнения товаров по ID
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Product product = (Product) o;
        return id == product.id;
    }

    @Override
    public int hashCode() {
        return id;
    }
}