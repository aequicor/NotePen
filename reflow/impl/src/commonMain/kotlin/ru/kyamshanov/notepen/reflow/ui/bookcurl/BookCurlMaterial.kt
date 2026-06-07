package ru.kyamshanov.notepen.reflow.ui.bookcurl

import ru.kyamshanov.notepen.reflow.api.BookCurlMaterialId
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Физические свойства листа бумаги (сырые, измеримые величины — контракт хранения). Из них один раз
 * на смену материала/размера выводятся солверные константы ([BookCurlDerived]); сам солвер 0..1
 * абстракций больше не видит. Список расширяемый: новый материал — строка в [TABLE], новый параметр —
 * поле.
 *
 * @property thicknessMm толщина одного листа (калипер), мм — гонит EI (∝t³) и тонкую кромку
 * @property grammageGsm плотность по площади (масса/площадь), г/м² — гонит гравитацию и инерцию
 * @property youngsModulusGpa модуль Юнга (упругость волокна), ГПа — гонит EI и нерастяжимость
 * @property frictionStatic коэффициент трения о нижнюю страницу — гонит контакт/скольжение
 * @property dampingRatio коэффициент демпфирования осцилляции (ζ) — гонит затухание/осцилляцию
 * @property creaseTendency склонность к залому (0..1) — гонит локальные складки/волны
 * @property glossiness глянцевость (0..1) — гонит блик и контраст самозатенения
 * @property opacity непрозрачность (0..1) — просвет следующей страницы (опционально)
 */
internal data class BookCurlMaterial(
    val id: BookCurlMaterialId,
    val thicknessMm: Float,
    val grammageGsm: Float,
    val youngsModulusGpa: Float,
    val frictionStatic: Float,
    val dampingRatio: Float,
    val creaseTendency: Float,
    val glossiness: Float,
    val opacity: Float,
) {
    companion object {
        /** Таблица пресетов с реальными инженерными значениями (TAPPI/ISO). Числа — данные, не магия. */
        val TABLE: Map<BookCurlMaterialId, BookCurlMaterial> =
            listOf(
                BookCurlMaterial(BookCurlMaterialId.OFFICE, 0.104f, 80f, 4.5f, 0.50f, 0.22f, 0.40f, 0.10f, 0.93f),
                BookCurlMaterial(BookCurlMaterialId.BOOK, 0.090f, 65f, 4.0f, 0.48f, 0.24f, 0.42f, 0.08f, 0.90f),
                BookCurlMaterial(BookCurlMaterialId.NEWSPRINT, 0.070f, 48f, 3.0f, 0.55f, 0.50f, 0.80f, 0.05f, 0.88f),
                BookCurlMaterial(BookCurlMaterialId.COATED, 0.095f, 120f, 6.5f, 0.30f, 0.16f, 0.40f, 0.55f, 0.97f),
                BookCurlMaterial(BookCurlMaterialId.GLOSSY, 0.115f, 160f, 7.5f, 0.25f, 0.15f, 0.35f, 0.85f, 0.98f),
                BookCurlMaterial(BookCurlMaterialId.MATTE, 0.135f, 130f, 5.5f, 0.55f, 0.28f, 0.45f, 0.12f, 0.96f),
                BookCurlMaterial(BookCurlMaterialId.CARDBOARD, 0.55f, 300f, 3.5f, 0.45f, 0.35f, 0.75f, 0.12f, 0.99f),
            ).associateBy { it.id }

        val Default: BookCurlMaterial = TABLE.getValue(BookCurlMaterialId.OFFICE)

        /** EI офисной бумаги — опорная жёсткость; радиус/спред считаются относительно неё. */
        val EI_OFFICE: Float = eiOf(Default)

        fun of(id: BookCurlMaterialId): BookCurlMaterial = TABLE[id] ?: Default
    }
}

/**
 * Солверные константы, выведенные из [material] и ширины листа [widthPx] (px). Считаются один раз на
 * смену материала/размера — не на вершину и не на кадр.
 */
internal class BookCurlDerived(
    material: BookCurlMaterial,
    widthPx: Float,
) {
    /** Масса на единицу площади, кг/м². */
    val arealMass: Float = (material.grammageGsm * GSM_TO_KG).coerceAtLeast(1e-4f)

    /** Жёсткость на изгиб EI = E·t³/12 (на единицу ширины), Н·м. */
    val ei: Float = eiOf(material)

    /** Эласто-гравитационная длина L_g = (EI/(m·g))^(1/3), м — короче ⇒ глубже провис. */
    val lg: Float = (ei / (arealMass * GRAVITY)).coerceAtLeast(1e-9f).pow(1f / 3f)

    /** Радиус завитка, px: больше EI ⇒ крупнее и мягче загиб (замена константного радиуса). */
    val rCurl: Float

    /** Усиление провисания под весом (безразмерное) — замена прежнего sagFactor=weight/stiffness. */
    val sagGain: Float

    /** Сила локализации подъёма у захвата (дальние строки отстают тем сильнее, чем floppier лист). */
    val rowSagStrength: Float

    /** Ширина гауссовой локализации деформации у захвата (доля высоты): жёсткий — шире. */
    val gripSpread: Float

    /** Толщина видимой кромки, px (тонкая полоса, не «плита»). */
    val edgePx: Float

    /** Собственная частота осцилляции при оседании, рад/с. */
    val omega: Float

    /** Коэффициент демпфирования (ζ) — прямо из пресета. */
    val zeta: Float = material.dampingRatio

    /** Степень блика: матовый — широкий, глянцевый — узкий. */
    val glossShininess: Float = lerp(GLOSS_SHININESS_MIN, GLOSS_SHININESS_MAX, material.glossiness)

    val glossiness: Float = material.glossiness
    val friction: Float = material.frictionStatic
    val creaseTendency: Float = material.creaseTendency

    init {
        val w = widthPx.coerceAtLeast(1f)
        val pxPerMm = w / PAGE_WIDTH_MM
        val stiffRatio = (ei / BookCurlMaterial.EI_OFFICE).coerceIn(STIFF_RATIO_MIN, STIFF_RATIO_MAX)
        rCurl = (R_REF_FRAC * w * stiffRatio.pow(R_STIFF_EXP)).coerceIn(R_MIN_FRAC * w, R_MAX_FRAC * w)
        sagGain = (PAGE_WIDTH_M / lg).coerceIn(0f, SAG_CAP)
        rowSagStrength = (sagGain * ROW_SAG_K).coerceIn(0f, ROW_SAG_MAX)
        val normStiff01 = ((stiffRatio.pow(GRIP_STIFF_EXP) - STIFF_LO) / (STIFF_HI - STIFF_LO)).coerceIn(0f, 1f)
        gripSpread = lerp(GRIP_SPREAD_MIN, GRIP_SPREAD_MAX, normStiff01)
        edgePx = (material.thicknessMm * pxPerMm).coerceIn(0f, EDGE_PX_MAX)
        omega = (K_OMEGA * sqrt(ei / (arealMass * PAGE_WIDTH_M_4))).coerceIn(OMEGA_MIN, OMEGA_MAX)
    }
}

/** EI = E·t³/12 (Н·м на единицу ширины) из сырых свойств материала. */
private fun eiOf(material: BookCurlMaterial): Float {
    val tM = material.thicknessMm * MM_TO_M
    val inertia = tM * tM * tM / SECOND_MOMENT_DIV
    return material.youngsModulusGpa * PA_PER_GPA * inertia
}

private fun lerp(
    a: Float,
    b: Float,
    t: Float,
): Float = a + (b - a) * t.coerceIn(0f, 1f)

/** Номинальная физическая ширина страницы (мм) — мост между px и СИ. */
internal const val PAGE_WIDTH_MM = 150f
private const val PAGE_WIDTH_M = PAGE_WIDTH_MM / 1000f
private const val PAGE_WIDTH_M_4 = PAGE_WIDTH_M * PAGE_WIDTH_M * PAGE_WIDTH_M * PAGE_WIDTH_M
private const val GSM_TO_KG = 0.001f
private const val MM_TO_M = 0.001f
private const val GRAVITY = 9.81f
private const val SECOND_MOMENT_DIV = 12f
private const val PA_PER_GPA = 1e9f
private const val R_REF_FRAC = 0.085f
private const val R_STIFF_EXP = 0.4f
private const val R_MIN_FRAC = 0.05f
private const val R_MAX_FRAC = 0.22f
private const val STIFF_RATIO_MIN = 0.05f
private const val STIFF_RATIO_MAX = 30f
private const val SAG_CAP = 2.0f
private const val GRIP_STIFF_EXP = 0.2f

/** Границы нормировки жёсткости: STIFF_RATIO_MIN^0.2 и STIFF_RATIO_MAX^0.2. */
private const val STIFF_LO = 0.5493f
private const val STIFF_HI = 1.9744f
private const val GRIP_SPREAD_MIN = 0.22f
private const val GRIP_SPREAD_MAX = 0.55f

/** Множитель/потолок локализации подъёма у захвата (из sagGain): офис ≈ прежние 0.54. */
private const val ROW_SAG_K = 0.33f
private const val ROW_SAG_MAX = 0.8f
private const val EDGE_PX_MAX = 3f
private const val K_OMEGA = 6f
private const val OMEGA_MIN = 8f
private const val OMEGA_MAX = 40f
private const val GLOSS_SHININESS_MIN = 8f
private const val GLOSS_SHININESS_MAX = 200f
