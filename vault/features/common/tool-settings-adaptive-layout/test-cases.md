# Test Cases — ToolSettingsFloatingPanel Adaptive Layout

**Spec:** `vault/features/common/tool-settings-adaptive-layout/spec.md`

---

## Unit — greedyFit

| ID | Сценарий | Входные данные | Ожидаемый результат | AC | Status |
|----|----------|----------------|---------------------|----|--------|
| TC-01 | Все slot-ы помещаются | maxWidth=600px, widths=[100,120,80], gap=12, padding=32, iconBtn=40 | [true, true, true] | AC-3 | PENDING |
| TC-02 | Ни один не помещается | maxWidth=50px, widths=[100,120,80], gap=12, padding=32, iconBtn=40 | [false, false, false] | AC-3 | PENDING |
| TC-03 | Только первый помещается | maxWidth=200px, widths=[100,200,80], gap=12, padding=32, iconBtn=40 | [true, false, false] | AC-3 | PENDING |
| TC-04 | Первые два помещаются | maxWidth=350px, widths=[100,120,200], gap=12, padding=32, iconBtn=40 | [true, true, false] | AC-3 | PENDING |
| TC-05 | maxWidth точно равен ширине slot-а + padding | Подобрать так, чтобы fits[0]=true, fits[1]=false | [true, false] | AC-3,EC-3 | PENDING |

---

## State — expanded slot logic

| ID | Сценарий | Действие | Ожидаемый результат | AC | Status |
|----|----------|----------|---------------------|----|--------|
| TC-06 | Раскрытие первого slot-а | tap icon[0] | expandedIndex == 0 | AC-4 | PENDING |
| TC-07 | Повторный тап на тот же slot | tap icon[0] (уже открыт) | expandedIndex == null | AC-5 | PENDING |
| TC-08 | Раскрытие другого slot-а | expandedIndex=0, tap icon[1] | expandedIndex == 1 | AC-6 | PENDING |
| TC-09 | Смена toolMode сбрасывает expandedIndex | PEN→ERASER | expandedIndex == null | EC-4 | PENDING |
