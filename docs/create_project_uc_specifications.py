from pathlib import Path

from docx import Document
from docx.enum.section import WD_SECTION_START
from docx.enum.style import WD_STYLE_TYPE
from docx.enum.table import WD_ALIGN_VERTICAL, WD_CELL_VERTICAL_ALIGNMENT
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from docx.shared import Inches, Pt, RGBColor


OUTPUT = Path(__file__).with_name("Project_Management_Use_Case_Specifications.docx")


def set_run_font(run, name="Times New Roman", size=10, bold=None, italic=None, color=None):
    run.font.name = name
    run._element.rPr.rFonts.set(qn("w:ascii"), name)
    run._element.rPr.rFonts.set(qn("w:hAnsi"), name)
    run._element.rPr.rFonts.set(qn("w:eastAsia"), name)
    run.font.size = Pt(size)
    if bold is not None:
        run.bold = bold
    if italic is not None:
        run.italic = italic
    if color:
        run.font.color.rgb = RGBColor.from_string(color)


def set_cell_margins(cell, top=80, start=120, bottom=80, end=120):
    tc = cell._tc
    tc_pr = tc.get_or_add_tcPr()
    margins = tc_pr.first_child_found_in("w:tcMar")
    if margins is None:
        margins = OxmlElement("w:tcMar")
        tc_pr.append(margins)
    for side, value in (("top", top), ("start", start), ("bottom", bottom), ("end", end)):
        node = margins.find(qn(f"w:{side}"))
        if node is None:
            node = OxmlElement(f"w:{side}")
            margins.append(node)
        node.set(qn("w:w"), str(value))
        node.set(qn("w:type"), "dxa")


def set_cell_width(cell, width_dxa):
    tc_pr = cell._tc.get_or_add_tcPr()
    tc_w = tc_pr.find(qn("w:tcW"))
    if tc_w is None:
        tc_w = OxmlElement("w:tcW")
        tc_pr.append(tc_w)
    tc_w.set(qn("w:w"), str(width_dxa))
    tc_w.set(qn("w:type"), "dxa")


def set_table_geometry(table, widths):
    table.autofit = False
    tbl_pr = table._tbl.tblPr
    layout = tbl_pr.first_child_found_in("w:tblLayout")
    if layout is None:
        layout = OxmlElement("w:tblLayout")
        tbl_pr.append(layout)
    layout.set(qn("w:type"), "fixed")

    tbl_w = tbl_pr.first_child_found_in("w:tblW")
    if tbl_w is None:
        tbl_w = OxmlElement("w:tblW")
        tbl_pr.append(tbl_w)
    tbl_w.set(qn("w:w"), str(sum(widths)))
    tbl_w.set(qn("w:type"), "dxa")

    tbl_ind = tbl_pr.first_child_found_in("w:tblInd")
    if tbl_ind is None:
        tbl_ind = OxmlElement("w:tblInd")
        tbl_pr.append(tbl_ind)
    tbl_ind.set(qn("w:w"), "120")
    tbl_ind.set(qn("w:type"), "dxa")

    grid = table._tbl.tblGrid
    for child in list(grid):
        grid.remove(child)
    for width in widths:
        col = OxmlElement("w:gridCol")
        col.set(qn("w:w"), str(width))
        grid.append(col)

    for row in table.rows:
        for idx, cell in enumerate(row.cells):
            set_cell_margins(cell)
            cell.vertical_alignment = WD_CELL_VERTICAL_ALIGNMENT.TOP
            if idx < len(widths):
                set_cell_width(cell, widths[idx])


def add_bottom_border(paragraph, color="808080", size="6"):
    p_pr = paragraph._p.get_or_add_pPr()
    p_bdr = p_pr.find(qn("w:pBdr"))
    if p_bdr is None:
        p_bdr = OxmlElement("w:pBdr")
        p_pr.append(p_bdr)
    bottom = OxmlElement("w:bottom")
    bottom.set(qn("w:val"), "single")
    bottom.set(qn("w:sz"), size)
    bottom.set(qn("w:space"), "1")
    bottom.set(qn("w:color"), color)
    p_bdr.append(bottom)


def add_page_field(paragraph):
    run = paragraph.add_run("Page ")
    set_run_font(run, size=9)
    fld_char1 = OxmlElement("w:fldChar")
    fld_char1.set(qn("w:fldCharType"), "begin")
    instr_text = OxmlElement("w:instrText")
    instr_text.set(qn("xml:space"), "preserve")
    instr_text.text = "PAGE"
    fld_char2 = OxmlElement("w:fldChar")
    fld_char2.set(qn("w:fldCharType"), "end")
    run._r.append(fld_char1)
    run._r.append(instr_text)
    run._r.append(fld_char2)


def setup_document():
    doc = Document()
    sec = doc.sections[0]
    sec.top_margin = Inches(1)
    sec.bottom_margin = Inches(1)
    sec.left_margin = Inches(1)
    sec.right_margin = Inches(1)
    sec.header_distance = Inches(0.492)
    sec.footer_distance = Inches(0.492)

    normal = doc.styles["Normal"]
    normal.font.name = "Times New Roman"
    normal._element.rPr.rFonts.set(qn("w:ascii"), "Times New Roman")
    normal._element.rPr.rFonts.set(qn("w:hAnsi"), "Times New Roman")
    normal.font.size = Pt(10)
    normal.paragraph_format.space_before = Pt(0)
    normal.paragraph_format.space_after = Pt(3)
    normal.paragraph_format.line_spacing = 1.08

    for name, size, color, before, after in (
        ("Heading 1", 16, "2E74B5", 16, 8),
        ("Heading 2", 13, "2E74B5", 12, 6),
        ("Heading 3", 12, "1F4D78", 8, 4),
    ):
        style = doc.styles[name]
        style.font.name = "Times New Roman"
        style._element.rPr.rFonts.set(qn("w:ascii"), "Times New Roman")
        style._element.rPr.rFonts.set(qn("w:hAnsi"), "Times New Roman")
        style.font.size = Pt(size)
        style.font.color.rgb = RGBColor.from_string(color)
        style.font.bold = True
        style.paragraph_format.space_before = Pt(before)
        style.paragraph_format.space_after = Pt(after)

    header = sec.header.paragraphs[0]
    header.alignment = WD_ALIGN_PARAGRAPH.LEFT
    r = header.add_run("LabTimeSheetSystem - Project Management Use Case Specifications")
    set_run_font(r, size=9, color="666666")
    add_bottom_border(header, color="B7B7B7", size="4")

    footer = sec.footer.paragraphs[0]
    footer.alignment = WD_ALIGN_PARAGRAPH.RIGHT
    add_page_field(footer)
    return doc


def add_text(cell, text, bold=False, centered=False):
    cell.text = ""
    p = cell.paragraphs[0]
    p.paragraph_format.space_before = Pt(0)
    p.paragraph_format.space_after = Pt(0)
    p.paragraph_format.line_spacing = 1.08
    if centered:
        p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    r = p.add_run(text)
    set_run_font(r, bold=bold)
    return p


def add_label(cell, label):
    p = add_text(cell, label, bold=True, centered=True)
    p.paragraph_format.space_before = Pt(1)
    p.paragraph_format.space_after = Pt(1)


def add_numbering_definition(doc, start=1):
    numbering = doc.part.numbering_part.element
    existing = [int(n.get(qn("w:abstractNumId"))) for n in numbering.findall(qn("w:abstractNum"))]
    abstract_id = max(existing, default=0) + 1
    existing_num = [int(n.get(qn("w:numId"))) for n in numbering.findall(qn("w:num"))]
    num_id = max(existing_num, default=0) + 1

    abstract = OxmlElement("w:abstractNum")
    abstract.set(qn("w:abstractNumId"), str(abstract_id))
    level = OxmlElement("w:lvl")
    level.set(qn("w:ilvl"), "0")
    start_el = OxmlElement("w:start")
    start_el.set(qn("w:val"), str(start))
    num_fmt = OxmlElement("w:numFmt")
    num_fmt.set(qn("w:val"), "decimal")
    lvl_text = OxmlElement("w:lvlText")
    lvl_text.set(qn("w:val"), "%1.")
    lvl_jc = OxmlElement("w:lvlJc")
    lvl_jc.set(qn("w:val"), "left")
    p_pr = OxmlElement("w:pPr")
    ind = OxmlElement("w:ind")
    ind.set(qn("w:left"), "360")
    ind.set(qn("w:hanging"), "180")
    p_pr.append(ind)
    level.extend([start_el, num_fmt, lvl_text, lvl_jc, p_pr])
    abstract.append(level)
    numbering.append(abstract)

    num = OxmlElement("w:num")
    num.set(qn("w:numId"), str(num_id))
    abs_ref = OxmlElement("w:abstractNumId")
    abs_ref.set(qn("w:val"), str(abstract_id))
    num.append(abs_ref)
    numbering.append(num)
    return num_id


def add_numbered_items(doc, cell, items):
    cell.text = ""
    num_id = add_numbering_definition(doc)
    for idx, item in enumerate(items):
        p = cell.paragraphs[0] if idx == 0 else cell.add_paragraph()
        p.paragraph_format.space_before = Pt(0)
        p.paragraph_format.space_after = Pt(1)
        p.paragraph_format.line_spacing = 1.06
        p_pr = p._p.get_or_add_pPr()
        num_pr = OxmlElement("w:numPr")
        ilvl = OxmlElement("w:ilvl")
        ilvl.set(qn("w:val"), "0")
        num = OxmlElement("w:numId")
        num.set(qn("w:val"), str(num_id))
        num_pr.extend([ilvl, num])
        p_pr.append(num_pr)
        r = p.add_run(item)
        set_run_font(r)


def add_labeled_flows(doc, cell, flows):
    cell.text = ""
    for flow_index, (label, items) in enumerate(flows):
        p = cell.paragraphs[0] if flow_index == 0 else cell.add_paragraph()
        p.paragraph_format.space_before = Pt(0 if flow_index == 0 else 3)
        p.paragraph_format.space_after = Pt(1)
        p.paragraph_format.line_spacing = 1.06
        r = p.add_run(label)
        set_run_font(r, bold=True)
        num_id = add_numbering_definition(doc)
        for item in items:
            step = cell.add_paragraph()
            step.paragraph_format.space_before = Pt(0)
            step.paragraph_format.space_after = Pt(1)
            step.paragraph_format.line_spacing = 1.06
            p_pr = step._p.get_or_add_pPr()
            num_pr = OxmlElement("w:numPr")
            ilvl = OxmlElement("w:ilvl")
            ilvl.set(qn("w:val"), "0")
            num = OxmlElement("w:numId")
            num.set(qn("w:val"), str(num_id))
            num_pr.extend([ilvl, num])
            p_pr.append(num_pr)
            r = step.add_run(item)
            set_run_font(r)


def add_simple_lines(cell, lines):
    cell.text = ""
    for idx, line in enumerate(lines):
        p = cell.paragraphs[0] if idx == 0 else cell.add_paragraph()
        p.paragraph_format.space_before = Pt(0)
        p.paragraph_format.space_after = Pt(1)
        p.paragraph_format.line_spacing = 1.06
        r = p.add_run(line)
        set_run_font(r)


def add_use_case(doc, index, data):
    if index > 1:
        doc.add_page_break()
    title = doc.add_paragraph(style="Heading 2")
    title.paragraph_format.keep_with_next = True
    r = title.add_run(f"6.3.{index} {data['id']} - {data['title']}")
    set_run_font(r, size=13, bold=True, color="2E74B5")

    table = doc.add_table(rows=9, cols=2)
    table.style = "Table Grid"
    set_table_geometry(table, [1800, 7560])

    # The form keeps each actor field as a separate label-detail row so every
    # table row has fixed, auditable geometry.
    add_label(table.cell(0, 0), "Primary Actors")
    add_text(table.cell(0, 1), data["actors"])
    add_label(table.cell(1, 0), "Secondary Actors")
    add_text(table.cell(1, 1), data.get("secondary", "None"))

    rows = [
        ("Description", "text", data["description"]),
        ("Preconditions", "numbered", data["preconditions"]),
        ("Postconditions", "lines", data["postconditions"]),
        ("Normal Sequences / Flow", "numbered", data["normal"]),
        ("Alternative Sequences / Flow", "labeled", data["alternatives"]),
        ("Exception Flow", "labeled", data["exceptions"]),
        ("Business Rule", "lines", [data["rules"]]),
    ]
    for row_index, (label, kind, value) in enumerate(rows, start=2):
        add_label(table.cell(row_index, 0), label)
        detail = table.cell(row_index, 1)
        if kind == "text":
            add_text(detail, value)
        elif kind == "numbered":
            add_numbered_items(doc, detail, value)
        elif kind == "labeled":
            add_labeled_flows(doc, detail, value)
        else:
            add_simple_lines(detail, value)

    # Keep the labels visually stable when the detail rows grow across pages.
    for row in table.rows:
        for cell in row.cells:
            cell.vertical_alignment = WD_ALIGN_VERTICAL.TOP


SPECS = [
    {
        "id": "UC01", "title": "View Owned Projects", "actors": "Mentor",
        "description": "Allows a Mentor to view the Projects owned by that Mentor and the Project actions currently available.",
        "preconditions": ["The Mentor is logged in with an active account."],
        "postconditions": ["No Project data is changed."],
        "normal": [
            "The Mentor opens the Owned Projects page.",
            "The system retrieves Projects owned by the Mentor.",
            "The system verifies that the Mentor may view the Project list.",
            "The system displays each visible Project with its status, current Leader, and available actions.",
        ],
        "alternatives": [("A1: At Step 2, the Mentor owns no Projects.", ["The system displays an empty Project list.", "The use case ends."])],
        "exceptions": [
            ("E1: At Step 1, the account is not active or the user is not authenticated.", ["The system denies access to the Owned Projects page.", "The use case ends."]),
            ("E2: At Step 2, Project data is temporarily unavailable.", ["The system informs the Mentor that the Project list cannot be loaded.", "The use case ends."]),
        ],
        "rules": "BR06, BR12, BR13, BR17",
    },
    {
        "id": "UC02", "title": "Create Project", "actors": "Mentor",
        "description": "Allows a Mentor to create a planned Project and establish its initial Project Leader.",
        "preconditions": ["The Mentor is logged in with an active account.", "An eligible active Intern is available to become the initial Project Leader."],
        "postconditions": ["A new PLANNED Project is created.", "The initial Leader membership and current leadership term are created."],
        "normal": [
            "The Mentor opens the Create Project form.",
            "The system displays the Project fields and eligible Interns.",
            "The Mentor enters Project information and selects an initial Project Leader.",
            "The system validates the Project information, ownership, and selected Intern eligibility.",
            "The system creates the PLANNED Project, initial membership, and current leadership term as one operation.",
            "The system displays the new Project details and confirmation.",
        ],
        "alternatives": [
            ("A1: At Step 2, no eligible Intern is available.", ["The system informs the Mentor that a Project cannot be created without an initial Leader.", "The use case ends."]),
            ("A2: At Step 4, entered Project information is incomplete or invalid.", ["The system identifies the invalid fields.", "The flow resumes at Step 3."]),
        ],
        "exceptions": [
            ("E1: At Step 1, the user is not an active Mentor.", ["The system denies access to the Create Project form.", "The use case ends."]),
            ("E2: At Step 4, the selected Intern is no longer eligible to lead the Project.", ["The system rejects the creation request without creating any Project data.", "The flow resumes at Step 3."]),
        ],
        "rules": "BR06, BR08, BR10, BR12, BR13, BR14, BR15, BR16",
    },
    {
        "id": "UC03", "title": "View Project Details", "actors": "Mentor, Intern / Project Leader",
        "description": "Allows an authorized user to view Project lifecycle information, members, current Leader, progress, and permitted actions.",
        "preconditions": ["The user is logged in with an active account.", "The Project exists and is within the user's visibility scope."],
        "postconditions": ["No Project data is changed."],
        "normal": [
            "The user selects a Project.",
            "The system retrieves the Project and its current context.",
            "The system verifies that the user may view the Project.",
            "The system displays Project details, members, current Leader, progress, lifecycle status, and allowed actions.",
        ],
        "alternatives": [("A1: At Step 4, the Project is completed or the user is a former member with history access.", ["The system displays the Project in read-only mode.", "The use case ends."])],
        "exceptions": [
            ("E1: At Step 3, the user is not permitted to view the Project.", ["The system denies access without revealing protected Project details.", "The use case ends."]),
            ("E2: At Step 2, the Project no longer exists or is unavailable.", ["The system informs the user that the Project cannot be found.", "The use case ends."]),
        ],
        "rules": "BR06, BR12, BR13, BR15, BR17, BR34",
    },
    {
        "id": "UC04", "title": "Add Project Member", "actors": "Mentor",
        "description": "Allows the owning Mentor to directly add an eligible Intern to a Project.",
        "preconditions": ["The Mentor is logged in with an active account.", "The Mentor owns the PLANNED or ACTIVE Project.", "The selected Intern is eligible and is not a current member of the Project."],
        "postconditions": ["A current Project membership is created for the selected Intern.", "Any pending invitation for the same Project and Intern is superseded."],
        "normal": [
            "The Mentor opens the Project membership area.",
            "The Mentor selects Add Project Member.",
            "The system displays eligible non-member Interns.",
            "The Mentor selects an Intern and confirms the addition.",
            "The system validates ownership, Project lifecycle, membership uniqueness, and Intern eligibility.",
            "The system creates the Project membership and supersedes a matching pending invitation, if one exists.",
            "The system displays the updated Project member list.",
        ],
        "alternatives": [("A1: At Step 3, no eligible non-member Intern is available.", ["The system displays that no Intern can currently be added.", "The use case ends."])],
        "exceptions": [
            ("E1: At Step 5, the selected Intern is already a current member or is no longer eligible.", ["The system creates no membership.", "The flow resumes at Step 3."]),
            ("E2: At Step 5, the Mentor does not own the selected Project.", ["The system denies the addition request.", "The use case ends."]),
        ],
        "rules": "BR06, BR08, BR10, BR12, BR13, BR16, BR18",
    },
    {
        "id": "UC05", "title": "Invite Project Member", "actors": "Project Leader",
        "description": "Allows the current Project Leader to invite an eligible non-member Intern to join the Project.",
        "preconditions": ["The Project Leader is logged in with an active Intern account.", "The Project has a valid current leadership term.", "The selected Intern is eligible and is not a current Project member."],
        "postconditions": ["A pending Project invitation tied to the current Leader term is created."],
        "normal": [
            "The Project Leader opens the Project invitation area.",
            "The Project Leader selects Invite Project Member.",
            "The system displays eligible Interns who are not current Project members.",
            "The Project Leader selects an Intern and confirms the invitation.",
            "The system validates the Leader term, recipient eligibility, and pending-invitation uniqueness.",
            "The system creates the pending invitation and records the issuing Leader term.",
            "The system confirms that the invitation has been issued.",
        ],
        "alternatives": [("A1: At Step 3, no eligible non-member Intern is available.", ["The system displays that no invitation can currently be issued.", "The use case ends."])],
        "exceptions": [
            ("E1: At Step 5, a pending invitation already exists for the selected Intern.", ["The system does not create a duplicate invitation.", "The flow resumes at Step 3."]),
            ("E2: At Step 5, the user is no longer the current Project Leader.", ["The system rejects the invitation request.", "The use case ends."]),
        ],
        "rules": "BR06, BR08, BR10, BR12, BR15, BR16, BR19, BR20",
    },
    {
        "id": "UC06", "title": "Revoke Project Invitation", "actors": "Mentor, Project Leader",
        "description": "Allows the owning Mentor to revoke any pending invitation or the issuing current Project Leader to revoke their own pending invitation.",
        "preconditions": ["The actor is logged in with an active account.", "A pending Project invitation exists."],
        "postconditions": ["The selected invitation is no longer pending and cannot be accepted."],
        "normal": [
            "The actor opens the Project invitation list.",
            "The actor selects a pending invitation.",
            "The system verifies that the actor is the owning Mentor or the issuing current Project Leader.",
            "The actor confirms revocation.",
            "The system revokes the invitation and records the result in invitation history.",
            "The system displays the updated invitation list.",
        ],
        "alternatives": [("A1: At Step 5, the invitation has already been accepted, declined, revoked, or invalidated.", ["The system shows the latest invitation status.", "The use case ends."])],
        "exceptions": [
            ("E1: At Step 3, the actor has no authority to revoke the invitation.", ["The system denies the revocation request.", "The use case ends."]),
            ("E2: At Step 3, the Leader's issuing term is no longer current.", ["The system denies revocation by that Leader.", "The use case ends."]),
        ],
        "rules": "BR06, BR12, BR15, BR20, BR21",
    },
    {
        "id": "UC07", "title": "Accept/Decline Project Invitation", "actors": "Intern",
        "description": "Allows the intended invited Intern to accept or decline a pending Project invitation.",
        "preconditions": ["The Intern is logged in with an active eligible account.", "A pending invitation addressed to that Intern exists."],
        "postconditions": ["If accepted, the Intern becomes a current Project member.", "If declined, no Project membership is created."],
        "normal": [
            "The Intern opens their pending Project invitations.",
            "The Intern selects an invitation.",
            "The system validates that the Intern is the intended recipient and remains eligible.",
            "The Intern selects Accept and confirms the response.",
            "The system creates the Project membership and records the accepted invitation response.",
            "The system displays the updated Project list and confirmation.",
        ],
        "alternatives": [("A1: At Step 4, the Intern selects Decline.", ["The system records the declined invitation response without creating a membership.", "The system confirms the decline.", "The use case ends."])],
        "exceptions": [
            ("E1: At Step 3, the invitation is no longer pending or the issuing Leader term is no longer valid.", ["The system invalidates the invitation where applicable and does not create a membership.", "The use case ends."]),
            ("E2: At Step 3, the Intern is no longer eligible or is already a current Project member.", ["The system rejects the response and does not create a membership.", "The use case ends."]),
        ],
        "rules": "BR06, BR08, BR10, BR12, BR16, BR20, BR22",
    },
    {
        "id": "UC08", "title": "Request Member Removal", "actors": "Project Leader",
        "description": "Allows the current Project Leader to request removal of another current Project member for Mentor decision.",
        "preconditions": ["The Project Leader is logged in with an active account and valid current leadership term.", "The target is another current Project member.", "The target has no pending exit request."],
        "postconditions": ["A pending member-removal exit request is created.", "The target remains a current member until the Mentor makes a decision."],
        "normal": [
            "The Project Leader opens the Project member list.",
            "The Project Leader selects a current member and selects Request Member Removal.",
            "The Project Leader enters a reason and confirms the request.",
            "The system validates the Leader term, target membership, reason, and pending-request uniqueness.",
            "The system creates a pending exit request for the target without closing membership.",
            "The system displays the pending request and available follow-up actions.",
        ],
        "alternatives": [("A1: At Step 3, the Project Leader cancels the confirmation.", ["The system creates no exit request.", "The use case ends."])],
        "exceptions": [
            ("E1: At Step 4, the selected target is the current Project Leader.", ["The system rejects the request and requires the Leader-removal process instead.", "The use case ends."]),
            ("E2: At Step 4, the target already has a pending exit request or is no longer a current member.", ["The system creates no duplicate request.", "The flow resumes at Step 2."]),
        ],
        "rules": "BR06, BR08, BR10, BR12, BR15, BR16, BR23, BR24",
    },
    {
        "id": "UC09", "title": "Request Project Exit", "actors": "Intern / Project Leader",
        "description": "Allows a current Project member to request their own exit from a Project for Mentor decision.",
        "preconditions": ["The actor is logged in with an active eligible Intern account.", "The actor is a current Project member.", "The actor has no pending exit request for the Project."],
        "postconditions": ["A pending self-exit request is created.", "The member remains active in the Project until the request is decided."],
        "normal": [
            "The member opens the selected Project.",
            "The member selects Request Project Exit.",
            "The member confirms the request.",
            "The system validates current membership and pending-request uniqueness.",
            "The system creates a pending self-exit request without closing membership.",
            "The system displays the pending request and its restrictions.",
        ],
        "alternatives": [("A1: At Step 3, the member cancels the confirmation.", ["The system creates no exit request.", "The use case ends."])],
        "exceptions": [
            ("E1: At Step 4, the member already has a pending exit request.", ["The system does not create another request and displays the existing pending request.", "The use case ends."]),
            ("E2: At Step 4, the member is no longer a current Project member.", ["The system rejects the request.", "The use case ends."]),
        ],
        "rules": "BR06, BR08, BR10, BR12, BR16, BR23, BR24, BR25",
    },
    {
        "id": "UC10", "title": "View Pending Exit Requests", "actors": "Mentor",
        "description": "Allows the owning Mentor to view pending member-removal and self-exit requests for a Project.",
        "preconditions": ["The Mentor is logged in with an active account.", "The Mentor owns the selected Project."],
        "postconditions": ["No Project membership or exit-request status is changed."],
        "normal": [
            "The Mentor opens the selected Project's pending exit requests.",
            "The system verifies that the Mentor owns the Project.",
            "The system retrieves pending exit requests and related Task-transfer status.",
            "The system displays each request, target member, requester, reason, and available decision actions.",
        ],
        "alternatives": [("A1: At Step 3, the Project has no pending exit requests.", ["The system displays an empty pending-request list.", "The use case ends."])],
        "exceptions": [
            ("E1: At Step 2, the Mentor does not own the Project.", ["The system denies access to the pending exit requests.", "The use case ends."]),
            ("E2: At Step 3, the Project is unavailable.", ["The system informs the Mentor that pending requests cannot be loaded.", "The use case ends."]),
        ],
        "rules": "BR06, BR12, BR13, BR17, BR23",
    },
    {
        "id": "UC11", "title": "Transfer Unfinished Tasks", "actors": "Project Leader",
        "description": "Allows the current Project Leader to transfer unfinished Tasks from a member with a pending exit request to another eligible current member.",
        "preconditions": ["The Project Leader is logged in with an active account and valid current leadership term.", "The target member has a pending exit request.", "At least one unfinished Task and an eligible receiving member exist."],
        "postconditions": ["Selected unfinished Tasks are reassigned to the chosen eligible member.", "Task history is retained."],
        "normal": [
            "The Project Leader opens the pending exit request.",
            "The Project Leader selects Transfer Unfinished Tasks.",
            "The system displays the target's unfinished Tasks and eligible receiving members.",
            "The Project Leader selects one or more Tasks, selects a receiving member, and confirms the transfer.",
            "The system validates the Leader term, pending exit state, Task state, and recipient eligibility.",
            "The system reassigns the selected unfinished Tasks while preserving their history.",
            "The system displays the updated Task assignments and exit-request status.",
        ],
        "alternatives": [("A1: At Step 3, the target has no unfinished Tasks.", ["The system shows that Task transfer is not required.", "The use case ends."])],
        "exceptions": [
            ("E1: At Step 5, the selected recipient is no longer an eligible current member.", ["The system does not transfer the Tasks.", "The flow resumes at Step 3."]),
            ("E2: At Step 5, the exit request is no longer pending or the user is no longer the current Leader.", ["The system rejects the transfer request.", "The use case ends."]),
        ],
        "rules": "BR06, BR08, BR10, BR12, BR15, BR16, BR23, BR24, BR26, BR43, BR44",
    },
    {
        "id": "UC12", "title": "Approve/Reject Exit Request", "actors": "Mentor",
        "description": "Allows the owning Mentor to approve or reject a pending member-removal or self-exit request.",
        "preconditions": ["The Mentor is logged in with an active account.", "The Mentor owns the Project.", "The selected exit request is pending."],
        "postconditions": ["If approved, the target membership is closed.", "If rejected, the target remains a current member."],
        "normal": [
            "The Mentor opens a pending exit request.",
            "The system displays the request, current membership, and unfinished-Task status.",
            "The Mentor selects Approve and confirms the decision.",
            "The system validates that the target is a current non-Leader member and has no unfinished Tasks.",
            "The system approves the request and closes the target's Project membership.",
            "The system records the decision and displays the updated Project member list.",
        ],
        "alternatives": [("A1: At Step 3, the Mentor selects Reject.", ["The system records the rejected decision.", "The system retains the target's membership and does not reverse completed Task transfers.", "The use case ends."])],
        "exceptions": [
            ("E1: At Step 4, the target has unfinished Tasks.", ["The system does not approve the exit request.", "The Mentor may use Transfer Unfinished Tasks and then retry at Step 3."]),
            ("E2: At Step 4, the target is the current Project Leader or is no longer a current member.", ["The system does not approve the request.", "The use case ends."]),
            ("E3: At Step 4, the Mentor does not own the Project.", ["The system denies the decision request.", "The use case ends."]),
        ],
        "rules": "BR06, BR12, BR13, BR15, BR23, BR24, BR26, BR27, BR28",
    },
    {
        "id": "UC13", "title": "Change Project Leader", "actors": "Mentor",
        "description": "Allows the owning Mentor to appoint an eligible current Project member as the new Project Leader.",
        "preconditions": ["The Mentor is logged in with an active account.", "The Mentor owns a PLANNED or ACTIVE Project with a current Leader.", "The selected replacement is an eligible current member without a pending exit request."],
        "postconditions": ["The previous leadership term is closed and a new current leadership term is created.", "Invitations issued under the previous Leader term are revoked."],
        "normal": [
            "The Mentor opens Project leadership management.",
            "The system displays the current Leader and eligible replacement members.",
            "The Mentor selects a replacement Leader and confirms the change.",
            "The system validates ownership, Project lifecycle, membership, eligibility, and pending-exit status.",
            "The system closes the former Leader term and creates the replacement's current Leader term.",
            "The system revokes pending invitations issued under the former Leader term.",
            "The system displays the updated Project leadership information.",
        ],
        "alternatives": [("A1: At Step 3, the Mentor cancels the change.", ["The system retains the current Leader and makes no change.", "The use case ends."])],
        "exceptions": [
            ("E1: At Step 4, the selected replacement is no longer eligible, is not a current member, or has a pending exit request.", ["The system does not change leadership.", "The flow resumes at Step 2."]),
            ("E2: At Step 4, the Project is completed or the Mentor does not own it.", ["The system denies the leadership change.", "The use case ends."]),
        ],
        "rules": "BR06, BR08, BR10, BR12, BR13, BR15, BR23, BR31, BR32",
    },
    {
        "id": "UC14", "title": "Remove Project Leader", "actors": "Mentor",
        "description": "Allows the owning Mentor to remove the current Project Leader after appointing a valid replacement Leader and handling unfinished Tasks.",
        "preconditions": ["The Mentor is logged in with an active account.", "The Mentor owns the Project.", "The selected member is the current Project Leader.", "An eligible replacement Leader is available."],
        "postconditions": ["The replacement becomes the current Project Leader.", "The former Leader membership is closed and unfinished Tasks are transferred when required."],
        "normal": [
            "The Mentor opens the Project member and leadership management area.",
            "The Mentor selects the current Leader for removal and selects an eligible replacement Leader.",
            "The system validates ownership, current leadership, replacement eligibility, and Project lifecycle.",
            "The system changes Project leadership to the replacement before removing the former Leader.",
            "The system transfers the former Leader's unfinished Tasks to the replacement Leader where required.",
            "The system closes the former Leader's membership and resolves any pending exit request for that member.",
            "The system displays the updated Leader and Project member list.",
        ],
        "alternatives": [("A1: At Step 2, the Mentor cancels the removal.", ["The system makes no leadership or membership change.", "The use case ends."])],
        "exceptions": [
            ("E1: At Step 3, no valid replacement Leader is available.", ["The system does not remove the current Leader.", "The use case ends."]),
            ("E2: At Step 3, the selected member is no longer the current Leader.", ["The system refreshes the Project leadership information.", "The use case ends."]),
        ],
        "rules": "BR06, BR12, BR13, BR15, BR29, BR30, BR31, BR32",
    },
    {
        "id": "UC15", "title": "Remove Project Member", "actors": "Mentor",
        "description": "Allows the owning Mentor to directly remove a current non-Leader Project member while preserving Project and Task consistency.",
        "preconditions": ["The Mentor is logged in with an active account.", "The Mentor owns the Project.", "The selected member is a current non-Leader Project member."],
        "postconditions": ["The selected membership is closed.", "Unfinished Tasks are transferred and any pending exit request is resolved."],
        "normal": [
            "The Mentor opens the Project member list.",
            "The Mentor selects a current non-Leader member and confirms removal.",
            "The system validates ownership, current membership, and that the target is not the current Project Leader.",
            "The system transfers the target's unfinished Tasks to the current Leader or selected eligible replacement, where required.",
            "The system closes the target's membership and resolves a pending exit request, if one exists.",
            "The system displays the updated Project member list and Task assignments.",
        ],
        "alternatives": [("A1: At Step 4, the target has no unfinished Tasks.", ["The system skips Task transfer and continues at Step 5."])],
        "exceptions": [
            ("E1: At Step 3, the selected member is the current Project Leader.", ["The system does not remove the member.", "The system requires the Remove Project Leader use case.", "The use case ends."]),
            ("E2: At Step 3, the target is no longer a current member or the Mentor does not own the Project.", ["The system denies the removal request.", "The use case ends."]),
        ],
        "rules": "BR06, BR12, BR13, BR15, BR23, BR29, BR30",
    },
    {
        "id": "UC16", "title": "Complete Project", "actors": "Mentor",
        "description": "Allows the owning Mentor to complete an ACTIVE Project after all required Tasks are done.",
        "preconditions": ["The Mentor is logged in with an active account.", "The Mentor owns an ACTIVE Project.", "Every non-deleted Task in the Project is DONE."],
        "postconditions": ["The Project becomes COMPLETED and read-only.", "Pending invitations and exit requests are resolved, and active membership and leadership terms are closed."],
        "normal": [
            "The Mentor opens Project leadership management or Project details.",
            "The Mentor selects Complete Project.",
            "The system displays the Project completion readiness summary.",
            "The Mentor confirms completion.",
            "The system validates ownership, ACTIVE status, and completion of every non-deleted Task.",
            "The system completes the Project, revokes pending invitations, resolves pending exit requests, and closes active membership and leadership terms.",
            "The system displays the completed Project in read-only mode.",
        ],
        "alternatives": [("A1: At Step 4, the Mentor cancels the confirmation.", ["The system keeps the Project ACTIVE.", "The use case ends."])],
        "exceptions": [
            ("E1: At Step 5, one or more non-deleted Tasks are not DONE.", ["The system does not complete the Project and identifies that completion requirements are not met.", "The use case ends."]),
            ("E2: At Step 5, the Project is already completed or the Mentor does not own it.", ["The system denies the completion request.", "The use case ends."]),
        ],
        "rules": "BR06, BR12, BR13, BR15, BR34, BR39, BR49",
    },
    {
        "id": "UC17", "title": "View Project History", "actors": "Mentor, Intern / Project Leader",
        "description": "Allows an authorized user to view read-only historical information for completed Projects, former members, invitations, exits, leadership, and Tasks.",
        "preconditions": ["The user is logged in with an active account or has permitted historical access.", "The completed Project or historical record is within the user's visibility scope."],
        "postconditions": ["No historical Project data is changed."],
        "normal": [
            "The user opens Project History or selects history for a visible completed Project.",
            "The system retrieves historical Project records within the user's visibility scope.",
            "The system verifies historical-access permission.",
            "The system displays the Project timeline, invitations, exits, former members, leadership terms, and historical Task information in read-only mode.",
        ],
        "alternatives": [("A1: At Step 2, no historical Project record is available for the selected scope.", ["The system displays an empty history result.", "The use case ends."])],
        "exceptions": [
            ("E1: At Step 3, the user does not have historical access to the Project.", ["The system denies access without revealing protected historical information.", "The use case ends."]),
            ("E2: At Step 2, the selected historical Project record cannot be found.", ["The system informs the user that the record is unavailable.", "The use case ends."]),
        ],
        "rules": "BR06, BR12, BR13, BR17, BR34, BR49",
    },
    {
        "id": "UC18", "title": "View My Projects", "actors": "Intern / Project Leader",
        "description": "Allows an Intern to view Projects in which they are a current member and the actions available in each Project.",
        "preconditions": ["The Intern is logged in with an active eligible account."],
        "postconditions": ["No Project data is changed."],
        "normal": [
            "The Intern opens the My Projects page.",
            "The system retrieves Projects in which the Intern has a current membership.",
            "The system verifies the Intern's visibility and lifecycle permissions for each Project.",
            "The system displays visible Projects with their lifecycle status, current Leader, membership context, and available actions.",
        ],
        "alternatives": [("A1: At Step 2, the Intern has no current Project memberships.", ["The system displays an empty My Projects list.", "The use case ends."])],
        "exceptions": [
            ("E1: At Step 1, the account or internship status does not permit normal Project access.", ["The system denies access to My Projects.", "The use case ends."]),
            ("E2: At Step 2, Project data is temporarily unavailable.", ["The system informs the Intern that the Project list cannot be loaded.", "The use case ends."]),
        ],
        "rules": "BR06, BR08, BR10, BR12, BR13, BR16, BR17",
    },
]


def main():
    doc = setup_document()
    heading = doc.add_paragraph(style="Heading 1")
    heading.paragraph_format.keep_with_next = True
    r = heading.add_run("6.3 Project Management Use Case Specifications")
    set_run_font(r, size=16, bold=True, color="2E74B5")

    note = doc.add_paragraph()
    note.paragraph_format.space_after = Pt(8)
    r = note.add_run("Scope note: Project Leader is an Intern holding a current leadership term in a specific Project; it is not a separate global role.")
    set_run_font(r, size=10, italic=True, color="404040")

    for index, spec in enumerate(SPECS, start=1):
        add_use_case(doc, index, spec)

    doc.save(OUTPUT)
    print(OUTPUT)


if __name__ == "__main__":
    main()
