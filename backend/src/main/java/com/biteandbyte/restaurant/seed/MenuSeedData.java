package com.biteandbyte.restaurant.seed;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Sample menu inserted on first start: 8 categories with 3 dishes each. Images are
 * placeholders that show the dish name. Admins replace them with real photo URLs.
 */
final class MenuSeedData {

    static final int DEFAULT_STOCK = 20;

    record SeedCategory(String name, String description, List<SeedItem> items) {
    }

    record SeedItem(String name, String description, String price, boolean veg) {

        BigDecimal priceValue() {
            return new BigDecimal(price);
        }
    }

    static final List<SeedCategory> CATEGORIES = List.of(
            new SeedCategory("Starters", "Crispy, smoky bites to begin the meal", List.of(
                    new SeedItem("Paneer Tikka", "Cottage cheese cubes marinated in spiced yoghurt and chargrilled", "249.00", true),
                    new SeedItem("Chicken 65", "Hyderabad-style fried chicken tossed with curry leaves and chillies", "279.00", false),
                    new SeedItem("Veg Spring Rolls", "Crisp rolls stuffed with stir-fried vegetables, served with chilli sauce", "189.00", true))),
            new SeedCategory("Main Course", "Rich curries and gravies, best with naan or rice", List.of(
                    new SeedItem("Butter Chicken", "Tandoori chicken simmered in a creamy tomato and butter gravy", "329.00", false),
                    new SeedItem("Paneer Butter Masala", "Paneer in a mildly sweet tomato, cashew and butter gravy", "279.00", true),
                    new SeedItem("Dal Makhani", "Black lentils slow-cooked overnight with butter and cream", "229.00", true))),
            new SeedCategory("Biryani", "Slow-cooked dum biryanis layered with fragrant basmati", List.of(
                    new SeedItem("Hyderabadi Chicken Dum Biryani", "Chicken and basmati sealed and cooked on dum, served with raita", "299.00", false),
                    new SeedItem("Mutton Biryani", "Tender mutton layered with saffron rice and fried onions", "389.00", false),
                    new SeedItem("Veg Dum Biryani", "Seasonal vegetables and basmati cooked on dum with whole spices", "229.00", true))),
            new SeedCategory("Tandoor & Grill", "Straight from the clay oven", List.of(
                    new SeedItem("Tandoori Chicken (Half)", "Half chicken marinated overnight and roasted in the tandoor", "319.00", false),
                    new SeedItem("Fish Tikka", "Boneless fish in ajwain and mustard marinade, chargrilled", "349.00", false),
                    new SeedItem("Butter Naan", "Soft leavened bread from the tandoor, brushed with butter", "49.00", true))),
            new SeedCategory("Rice & Noodles", "Indo-Chinese favourites and simple rice bowls", List.of(
                    new SeedItem("Veg Hakka Noodles", "Wok-tossed noodles with crunchy vegetables and soy", "199.00", true),
                    new SeedItem("Chicken Fried Rice", "Wok-fried rice with egg, chicken and spring onion", "229.00", false),
                    new SeedItem("Jeera Rice", "Basmati tempered with cumin and ghee", "149.00", true))),
            new SeedCategory("Desserts", "Something sweet to finish", List.of(
                    new SeedItem("Gulab Jamun (2 pcs)", "Warm milk dumplings soaked in cardamom sugar syrup", "99.00", true),
                    new SeedItem("Double Ka Meetha", "Hyderabadi bread pudding with saffron, milk and dry fruits", "129.00", true),
                    new SeedItem("Rasmalai (2 pcs)", "Soft cottage cheese discs in chilled saffron milk", "139.00", true))),
            new SeedCategory("Beverages", "Cool drinks and hot chai", List.of(
                    new SeedItem("Sweet Lassi", "Thick churned yoghurt drink, lightly sweetened", "89.00", true),
                    new SeedItem("Masala Chai", "Tea brewed with milk, ginger and cardamom", "49.00", true),
                    new SeedItem("Fresh Lime Soda", "Fresh lime with soda, sweet or salted", "79.00", true))),
            new SeedCategory("Combos & Meals", "Complete meals at a better price", List.of(
                    new SeedItem("Veg Thali", "Two curries, dal, rice, two rotis, salad and a dessert", "249.00", true),
                    new SeedItem("Non-Veg Thali", "Chicken curry, dal, rice, two rotis, salad and a dessert", "329.00", false),
                    new SeedItem("Biryani Combo", "Chicken dum biryani with raita, salan and gulab jamun", "349.00", false))));

    static String placeholderImage(String label) {
        return "https://placehold.co/600x400/png?text=" + URLEncoder.encode(label, StandardCharsets.UTF_8);
    }

    private MenuSeedData() {
    }
}
